package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.approval.api.SeedImportSummary;
import com.fitouts.approval.application.ScheduleTemplateSeedImporter;
import com.fitouts.approval.application.SeedValueParser;
import com.fitouts.schedule.domain.LockedConstraintRule;
import com.fitouts.schedule.domain.LockedConstraintRuleRepository;
import com.fitouts.schedule.domain.ProductivityNorm;
import com.fitouts.schedule.domain.ProductivityNormRepository;
import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.ScheduleTemplateRepository;
import com.fitouts.schedule.domain.ScopeToggle;
import com.fitouts.schedule.domain.ScopeToggleRepository;
import com.fitouts.schedule.domain.TemplateActivity;
import com.fitouts.schedule.domain.TemplateActivityRepository;
import com.fitouts.schedule.domain.TemplateDependency;
import com.fitouts.schedule.domain.TemplateDependencyRepository;
import com.fitouts.schedule.domain.TemplateProcurementItem;
import com.fitouts.schedule.domain.TemplateProcurementItemRepository;
import com.fitouts.schedule.domain.TradePackage;
import com.fitouts.schedule.domain.TradePackageRepository;
import com.fitouts.schedule.engine.ScheduleParameters;
import com.fitouts.schedule.engine.WorkingCalendar;

import lombok.RequiredArgsConstructor;

/**
 * Imports the schedule half of the VetroBuild seed file: templates, activities, dependency
 * grammar, long-lead matrix, locked constraints and productivity norms.
 *
 * <p>Two deliberate departures from the source data:
 * <ul>
 *   <li>{@code start_wd} and {@code finish_wd} are <b>not</b> used for scheduling. They are
 *       stored on the activity so the variance is visible, but dates come from CPM over
 *       {@code dur_wd} and {@code predecessor}. The two disagree on a third of activities, and
 *       the dependency grammar is the version that can actually be executed on site.</li>
 *   <li>No activity carries a {@code scaling_method}, so it is derived from the trade package
 *       plus the locked-constraint and long-lead lists.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScheduleSeedImportService implements ScheduleTemplateSeedImporter {

    /** "G120", "G120FS", "G120SS+3", "G090, G100" — the grammar the seed uses for predecessors. */
    private static final Pattern PREDECESSOR = Pattern.compile(
            "([A-Z]{1,3}\\d{2,4})\\s*(FS|SS|FF|SF)?\\s*([+-]\\s*\\d+)?", Pattern.CASE_INSENSITIVE);

    /**
     * Trades that are genuinely optional on a villa, and the scope question that decides
     * whether their activities exist at all.
     *
     * <p>The seed has no scope-toggle sheet, so this derives one from the trade packages. Only
     * whole trades that can be absent from a project are listed: you can build a villa without
     * a pool or a garden, but not without civil works, so nothing structural appears here.
     */
    private static final Map<String, DerivedToggle> OPTIONAL_TRADE_SCOPES = Map.of(
            "PKG-LAN", new DerivedToggle("LANDSCAPE", "Landscaping and irrigation in scope?"),
            "PKG-POO", new DerivedToggle("SWIMMING_POOL", "Swimming pool in scope?"),
            "PKG-SMT", new DerivedToggle("SMART_HOME", "Smart home and AV in scope?"),
            "PKG-SEC", new DerivedToggle("SECURITY_SYSTEM", "CCTV, access control and security in scope?"));

    private record DerivedToggle(String code, String label) {
    }

    private final ScheduleTemplateRepository templateRepository;
    private final TemplateActivityRepository activityRepository;
    private final TemplateDependencyRepository dependencyRepository;
    private final TemplateProcurementItemRepository procurementRepository;
    private final LockedConstraintRuleRepository lockedConstraintRepository;
    private final ProductivityNormRepository productivityNormRepository;
    private final ScopeToggleRepository scopeToggleRepository;
    private final TradePackageRepository tradePackageRepository;
    private final TradePackageCatalogue tradePackageCatalogue;
    private final TemplatePlanner planner;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void importTemplates(JsonNode root, SeedImportSummary summary) {
        importScopeToggles(root, summary);
        importTradePackages(root, summary);
        importLockedConstraints(root, summary);
        importProductivityNorms(root, summary);
        importTemplateLibrary(root, summary);
        importLongLeadMatrix(root, summary);
        recomputeTemplateLengths(summary);
    }

    // -------------------------------------------------------- trade packages

    /**
     * Loads the subcontract package catalogue, which the template activities' prose trade
     * labels are matched against when the apply cascade builds package shells.
     */
    private void importTradePackages(JsonNode root, SeedImportSummary summary) {
        JsonNode rows = section(root, "sc_trade_packages", "trade_packages");
        if (rows == null || !rows.isArray()) {
            summary.warn("No sc_trade_packages section; template trades will not produce package shells.");
            return;
        }

        int inserted = 0;
        int updated = 0;
        int order = 0;
        for (JsonNode node : rows) {
            String code = text(node, "package_code", "code");
            if (code == null) continue;
            code = code.toUpperCase(Locale.ROOT);

            boolean isNew = upsertTradePackage(code,
                    firstNonNull(text(node, "trade_package", "name"), code),
                    text(node, "typical_boq_sections", "boq_sections"),
                    text(node, "special_licence_required", "licence"),
                    text(node, "typical_retention", "retention"),
                    text(node, "typical_payment_terms", "payment_terms"),
                    order++);
            if (isNew) inserted++; else updated++;
        }

        if (upsertTradePackage(TradePackageCatalogue.DERIVED_MEP_CODE, TradePackageCatalogue.DERIVED_MEP_NAME,
                "Combined mechanical, electrical and plumbing scope", null, null, null, order)) {
            inserted++;
            summary.warn("Added " + TradePackageCatalogue.DERIVED_MEP_CODE + " to the package catalogue. The "
                    + "templates label a block of services work simply \"MEP\", which the seed's package sheet "
                    + "has no row for, so those activities would otherwise produce no package shell.");
        }

        summary.countInserted("trade_package", inserted);
        summary.countUpdated("trade_package", updated);
    }

    private boolean upsertTradePackage(String code, String name, String boqSections, String licence,
                                       String retention, String paymentTerms, int sortOrder) {
        TradePackage pkg = tradePackageRepository.findByCodeAndCompanyIdIsNull(code).orElseGet(TradePackage::new);
        boolean isNew = pkg.getUuid() == null;
        pkg.setCompanyId(null);
        pkg.setCode(code);
        pkg.setName(name);
        pkg.setTypicalBoqSections(boqSections);
        pkg.setSpecialLicenceRequired(licence);
        pkg.setTypicalRetention(retention);
        pkg.setTypicalPaymentTerms(paymentTerms);
        pkg.setMatchKeywords(firstNonNull(TradePackageCatalogue.seedKeywords(code), name.toLowerCase(Locale.ROOT)));
        pkg.setSortOrder(sortOrder);
        pkg.setActive(true);
        tradePackageRepository.save(pkg);
        tradePackageRepository.flush();
        return isNew;
    }

    // ------------------------------------------------------------- templates

    private void importTemplateLibrary(JsonNode root, SeedImportSummary summary) {
        JsonNode templates = section(root, "schedule_templates", "templates");
        if (templates == null || !templates.isArray()) {
            summary.warn("No schedule_templates section in the seed file; the template library was left alone.");
            return;
        }

        TradePackageCatalogue.Lookup tradePackages = tradePackageCatalogue.lookup();
        Set<String> unmappedTrades = new java.util.TreeSet<>();
        Set<DerivedToggle> derivedToggles = new java.util.LinkedHashSet<>();

        for (JsonNode node : templates) {
            String code = text(node, "template_code", "code", "id");
            if (code == null) {
                summary.warn("A template row has no code and was skipped.");
                continue;
            }

            ScheduleTemplate template = templateRepository.findByCodeAndCompanyIdIsNull(code)
                    .orElseGet(ScheduleTemplate::new);
            boolean isNew = template.getUuid() == null;

            template.setCompanyId(null);
            template.setCode(code);
            template.setName(firstNonNull(text(node, "template_name", "name"), code));
            template.setProjectType(text(node, "project_type", "type"));
            template.setDescription(text(node, "description", "notes"));
            template.setTargetCalendarDays(intOf(node, "target_calendar_days", "calendar_days", "duration_days"));
            template.setTargetWorkingDays(
                    intOf(node, "total_working_days", "target_working_days", "working_days", "dur_wd"));
            template.setWorkWeek(text(node, "work_week", "working_week"));
            template.setSystemTemplate(true);
            template.setBaseParametersJson(baseParametersJson(node));

            templateRepository.save(template);

            // Templates are re-imported wholesale: partial merges of a 133-activity network
            // produce networks nobody can reason about.
            activityRepository.deleteByTemplateUuid(template.getUuid());
            dependencyRepository.deleteByTemplateUuid(template.getUuid());
            activityRepository.flush();

            int activityCount = importActivities(node, template, tradePackages, unmappedTrades,
                    derivedToggles, summary);
            applyFastTrack(template, node, summary);
            templateRepository.save(template);
            summary.countInserted("template_activity", activityCount);
            if (isNew) summary.countInserted("schedule_template", 1);
            else summary.countUpdated("schedule_template", 1);
        }

        persistDerivedToggles(derivedToggles, summary);

        if (!unmappedTrades.isEmpty()) {
            summary.warn("These template trade labels match no package in the catalogue, so their activities "
                    + "produce no package shell: " + String.join(", ", unmappedTrades)
                    + ". In-house and consultant labels such as design, PRO and procurement are expected here; "
                    + "anything else needs a catalogue row or a keyword.");
        }
    }

    /**
     * Marks a compressed programme as fast-track and works out what the client has to accept
     * before it can be published.
     *
     * <p>The seed has no conditions list, but the fast-track template's activities carry them
     * in their constraint notes: stock profiles only, no bespoke veneer, rapid-set screed
     * mandatory, the flood test cannot be shortened. Notes that state a restriction are
     * promoted to conditions; notes that merely describe the method are not.
     */
    private void applyFastTrack(ScheduleTemplate template, JsonNode node, SeedImportSummary summary) {
        List<String> conditions = new ArrayList<>(stringList(node, "fast_track_conditions", "conditions"));
        boolean flagged = Boolean.TRUE.equals(boolOf(node, "is_fast_track", "fast_track"))
                || looksFastTrack(template);

        if (conditions.isEmpty() && flagged) {
            for (TemplateActivity ta : activityRepository.findByTemplateUuidOrderBySortOrderAsc(template.getUuid())) {
                if (isRestriction(ta.getConstraintNote())) conditions.add(ta.getConstraintNote());
            }
            if (!conditions.isEmpty()) {
                summary.warn("Template " + template.getCode() + ": the seed lists no fast-track conditions, so "
                        + conditions.size() + " were derived from the activities' own constraint notes. The "
                        + "programme cannot be published until the client acknowledges each one.");
            }
        }

        template.setFastTrack(flagged || !conditions.isEmpty());
        template.setFastTrackConditionsJson(conditions.isEmpty() ? null : writeJson(conditions));
    }

    private boolean looksFastTrack(ScheduleTemplate template) {
        String haystack = ((template.getCode() == null ? "" : template.getCode()) + " "
                + (template.getName() == null ? "" : template.getName())).toLowerCase(Locale.ROOT);
        return haystack.contains("fast track") || haystack.contains("fast-track");
    }

    /** A note the client has to agree to, as opposed to one that just describes the method. */
    private boolean isRestriction(String note) {
        if (note == null) return false;
        String lower = note.toLowerCase(Locale.ROOT);
        return lower.contains(" only") || lower.contains("no ") || lower.contains("requires")
                || lower.contains("mandatory") || lower.contains("risk") || lower.contains("cannot")
                || lower.contains("impossible");
    }

    /**
     * Creates the toggle rows the derived activity tags point at. Each defaults to on, so a
     * project that says nothing keeps the full template and nobody loses scope by silence.
     */
    private void persistDerivedToggles(Set<DerivedToggle> derived, SeedImportSummary summary) {
        int inserted = 0;
        for (DerivedToggle toggle : derived) {
            ScopeToggle row = scopeToggleRepository.findByCodeAndCompanyIdIsNull(toggle.code())
                    .orElseGet(ScopeToggle::new);
            if (row.getUuid() != null) continue;
            row.setCompanyId(null);
            row.setCode(toggle.code());
            row.setLabel(toggle.label());
            row.setDefaultOn(true);
            row.setDescription("Derived from the trade package on the template activities. "
                    + "Turning it off removes those activities and re-solves the programme.");
            scopeToggleRepository.save(row);
            inserted++;
        }
        if (inserted > 0) {
            summary.countInserted("scope_toggle", inserted);
            summary.warn("The seed has no scope-toggle sheet, so " + inserted + " toggles were derived from the "
                    + "optional trade packages (landscape, pool, smart home, security). Everything else in the "
                    + "templates is treated as always in scope.");
        }
    }

    private String derivedToggleCode(String tradePackageCode, Set<DerivedToggle> used) {
        DerivedToggle toggle = tradePackageCode == null ? null : OPTIONAL_TRADE_SCOPES.get(tradePackageCode);
        if (toggle == null) return null;
        used.add(toggle);
        return toggle.code();
    }

    private int importActivities(JsonNode templateNode, ScheduleTemplate template,
                                 TradePackageCatalogue.Lookup tradePackages, Set<String> unmappedTrades,
                                 Set<DerivedToggle> derivedToggles, SeedImportSummary summary) {
        JsonNode activities = section(templateNode, "activities", "template_activities");
        if (activities == null || !activities.isArray()) {
            summary.warn("Template " + template.getCode() + " has no activities section.");
            return 0;
        }

        Set<String> codes = new HashSet<>();
        List<TemplateActivity> saved = new ArrayList<>();
        Map<String, String> rawPredecessors = new LinkedHashMap<>();
        int order = 0;

        for (JsonNode node : activities) {
            String code = text(node, "activity_code", "code", "act_id", "id");
            if (code == null || !codes.add(code)) {
                summary.warn("Template " + template.getCode()
                        + " has a duplicate or unnamed activity code; the row was skipped.");
                continue;
            }

            TemplateActivity ta = new TemplateActivity();
            ta.setTemplateUuid(template.getUuid());
            ta.setActivityCode(code);
            ta.setName(firstNonNull(text(node, "activity_name", "name", "description", "activity"), code));
            ta.setWbsPhase(text(node, "phase", "wbs_phase", "wbs"));
            ta.setTradeLabel(text(node, "trade", "trade__package", "trade_package", "trade_name"));
            ta.setTradePackageCode(firstNonNull(
                    text(node, "trade_package_code", "package_code", "trade_code"),
                    tradePackages.codeForLabel(ta.getTradeLabel())));
            if (ta.getTradePackageCode() == null && ta.getTradeLabel() != null) {
                unmappedTrades.add(ta.getTradeLabel());
            }

            Integer duration = intOf(node, "dur_wd", "duration_wd", "duration_working_days", "duration");
            ta.setBaseDurationDays(duration == null ? 1 : Math.max(0, duration));
            ta.setMilestone(ta.getBaseDurationDays() == 0
                    || Boolean.TRUE.equals(boolOf(node, "is_milestone", "milestone")));
            if (ta.isMilestone()) ta.setBaseDurationDays(0);

            ta.setSeedStartWd(intOf(node, "start_wd", "start"));
            ta.setSeedFinishWd(intOf(node, "finish_wd", "finish"));
            ta.setConstraintNote(text(node, "constraint_note", "constraint__note", "constraint", "notes"));
            ta.setScopeToggleCode(firstNonNull(
                    normaliseToggle(text(node, "scope_toggle", "toggle", "scope_toggle_code")),
                    derivedToggleCode(ta.getTradePackageCode(), derivedToggles)));
            ta.setCriticalSeed(Boolean.TRUE.equals(boolOf(node, "is_critical", "critical")));
            ta.setSortOrder(order++);

            applyScaling(ta, node);
            saved.add(activityRepository.save(ta));

            String predecessor = text(node, "predecessor", "predecessors", "preds");
            if (predecessor != null) rawPredecessors.put(code, predecessor);
        }

        importDependencies(template, codes, rawPredecessors, summary);
        return saved.size();
    }

    /**
     * Parses the predecessor column. The seed only uses FS and SS with positive lag, but the
     * parser accepts all four types so a corrected source file needs no code change.
     */
    private void importDependencies(ScheduleTemplate template, Set<String> validCodes,
                                    Map<String, String> rawPredecessors, SeedImportSummary summary) {
        int written = 0;
        Set<String> seen = new HashSet<>();

        for (Map.Entry<String, String> entry : rawPredecessors.entrySet()) {
            String successor = entry.getKey();
            Matcher matcher = PREDECESSOR.matcher(entry.getValue());
            boolean matchedAny = false;

            while (matcher.find()) {
                String predecessor = matcher.group(1).toUpperCase(Locale.ROOT);
                if (!validCodes.contains(predecessor)) continue;
                if (predecessor.equals(successor)) continue;
                if (!seen.add(predecessor + ">" + successor)) continue;
                matchedAny = true;

                TemplateDependency dep = new TemplateDependency();
                dep.setTemplateUuid(template.getUuid());
                dep.setPredecessorCode(predecessor);
                dep.setSuccessorCode(successor);
                dep.setType(matcher.group(2) == null ? "FS" : matcher.group(2).toUpperCase(Locale.ROOT));
                dep.setLagDays(matcher.group(3) == null ? 0
                        : Integer.parseInt(matcher.group(3).replaceAll("\\s", "")));
                dependencyRepository.save(dep);
                written++;
            }

            if (!matchedAny && SeedValueParser.trimToNull(entry.getValue()) != null) {
                summary.warn("Template " + template.getCode() + ": could not parse predecessor '"
                        + entry.getValue() + "' for " + successor + ", so it has no logic links.");
            }
        }
        summary.countInserted("template_dependency", written);
    }

    /**
     * The seed carries no scaling method, so it is derived. Anything named in the locked
     * constraint list is fixed; anything with a measurable trade quantity is quantity-driven;
     * everything else scales parametrically.
     */
    private void applyScaling(TemplateActivity ta, JsonNode node) {
        String explicit = text(node, "scaling_method", "scaling");
        if (explicit != null) {
            String upper = explicit.trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("QUANT")) ta.setScalingMethod("QUANTITY");
            else if (upper.startsWith("FIX")) ta.setScalingMethod("FIXED");
            else ta.setScalingMethod("PARAMETRIC");
        } else if (isFixedByName(ta)) {
            ta.setScalingMethod("FIXED");
            ta.setLockedDuration(true);
        } else if (ta.getTradePackageCode() != null) {
            ta.setScalingMethod("QUANTITY");
        } else {
            ta.setScalingMethod("PARAMETRIC");
        }

        ta.setScalingDriver(firstNonNull(text(node, "scaling_driver", "driver"), "AREA"));
        ta.setCrewOutputRef(text(node, "crew_output_ref", "productivity_ref", "work_item"));
        if (ta.isMilestone()) ta.setScalingMethod("FIXED");
    }

    /** Cure, test and statutory-hold activities never scale with project size. */
    private boolean isFixedByName(TemplateActivity ta) {
        String name = ta.getName() == null ? "" : ta.getName().toLowerCase(Locale.ROOT);
        return name.contains("cur") || name.contains("flood test") || name.contains("dry")
                || name.contains("settl") || name.contains("notice period") || name.contains("hold");
    }

    // ---------------------------------------------------------- long lead

    private void importLongLeadMatrix(JsonNode root, SeedImportSummary summary) {
        JsonNode items = section(root, "long_lead_matrix", "long_lead_items", "procurement");
        if (items == null || !items.isArray()) {
            summary.warn("No long_lead_matrix section; no order-by dates will be computed.");
            return;
        }

        // Global items apply to every template; the planner picks them up alongside any
        // template-specific ones.
        procurementRepository.deleteAll(procurementRepository.findByTemplateUuidIsNull());
        int order = 0;
        for (JsonNode node : items) {
            String name = text(node, "item", "item_name", "name");
            if (name == null) continue;

            TemplateProcurementItem item = new TemplateProcurementItem();
            item.setItemName(name);
            String leadRaw = text(node, "typical_lead_calendar_days", "lead_time_days", "lead_time", "lead_days");
            item.setLeadTimeRaw(leadRaw);
            item.setLeadTimeCalendarDaysMin(SeedValueParser.minOf(leadRaw));
            item.setLeadTimeCalendarDaysMax(SeedValueParser.maxOf(leadRaw));
            item.setOrderByRule(text(node, "order_by_rule", "order_by", "rule"));
            item.setSiteInfoNeeded(text(node, "site_info_needed_before_order", "site_info_needed", "site_info",
                    "prerequisites"));
            item.setRiskNote(text(node, "risk_if_late", "risk", "risk_note", "notes"));
            item.setLinkedInstallActivityCode(text(node, "install_activity", "activity_code"));
            item.setMatchKeywords(keywordsFrom(name, text(node, "keywords", "match_keywords")));
            item.setSortOrder(order++);
            procurementRepository.save(item);
        }
        summary.countInserted("template_procurement_item", order);
    }

    // ------------------------------------------------------ locked constraints

    private void importLockedConstraints(JsonNode root, SeedImportSummary summary) {
        JsonNode rules = section(root, "locked_constraints", "constraints");
        if (rules == null || !rules.isArray()) return;

        int inserted = 0;
        int updated = 0;
        for (JsonNode node : rules) {
            String name = text(node, "constraint", "name", "constraint_name");
            if (name == null) continue;

            LockedConstraintRule rule = lockedConstraintRepository
                    .findByNameAndCompanyIdIsNull(name).orElseGet(LockedConstraintRule::new);
            boolean isNew = rule.getUuid() == null;

            rule.setCompanyId(null);
            rule.setName(name);
            String holdRaw = text(node, "minimum_hold", "min_hold", "hold_days", "minimum_duration");
            rule.setMinimumHoldRaw(holdRaw);
            rule.setMinimumHoldWorkingDays(SeedValueParser.holdWorkingDays(holdRaw));
            rule.setAppliesAfter(text(node, "applies_after", "after", "predecessor"));
            rule.setReason(text(node, "why_it_cannot_be_compressed", "reason", "why", "rationale"));
            rule.setEngineBehaviour(text(node, "engine_behaviour", "behaviour", "engine_rule"));
            rule.setCompressible(Boolean.TRUE.equals(boolOf(node, "is_compressible", "compressible")));
            rule.setMatchKeywords(keywordsFrom(name, text(node, "keywords", "match_keywords")));
            lockedConstraintRepository.save(rule);

            if (isNew) inserted++; else updated++;
        }
        summary.countInserted("locked_constraint_rule", inserted);
        summary.countUpdated("locked_constraint_rule", updated);
    }

    // ----------------------------------------------------- productivity norms

    private void importProductivityNorms(JsonNode root, SeedImportSummary summary) {
        JsonNode norms = section(root, "productivity_norms", "productivity");
        if (norms == null || !norms.isArray()) return;

        int inserted = 0;
        int updated = 0;
        for (JsonNode node : norms) {
            String workItem = text(node, "work_item", "item", "activity");
            if (workItem == null) continue;

            ProductivityNorm norm = productivityNormRepository
                    .findByWorkItemAndCompanyIdIsNull(workItem).orElseGet(ProductivityNorm::new);
            boolean isNew = norm.getUuid() == null;

            norm.setCompanyId(null);
            norm.setWorkItem(workItem);
            norm.setUnit(text(node, "unit", "uom"));
            String outputRaw = text(node, "output_per_crew_per_day", "output", "productivity");
            norm.setOutputRaw(outputRaw);
            Integer min = SeedValueParser.minOf(outputRaw);
            Integer max = SeedValueParser.maxOf(outputRaw);
            norm.setOutputPerCrewPerDayMin(min == null ? null : java.math.BigDecimal.valueOf(min));
            norm.setOutputPerCrewPerDayMax(max == null ? null : java.math.BigDecimal.valueOf(max));
            norm.setStandardCrew(text(node, "standard_crew", "crew"));
            norm.setNotes(text(node, "notes", "note"));
            norm.setMatchKeywords(keywordsFrom(workItem, text(node, "keywords", "match_keywords")));
            productivityNormRepository.save(norm);

            if (isNew) inserted++; else updated++;
        }
        summary.countInserted("productivity_norm", inserted);
        summary.countUpdated("productivity_norm", updated);
    }

    // ---------------------------------------------------------- scope toggles

    private void importScopeToggles(JsonNode root, SeedImportSummary summary) {
        JsonNode toggles = section(root, "scope_toggles", "toggles");
        if (toggles == null || !toggles.isArray()) return;

        int inserted = 0;
        for (JsonNode node : toggles) {
            String code = normaliseToggle(text(node, "toggle_code", "code", "toggle"));
            if (code == null) continue;

            ScopeToggle toggle = scopeToggleRepository.findByCodeAndCompanyIdIsNull(code)
                    .orElseGet(ScopeToggle::new);
            boolean isNew = toggle.getUuid() == null;
            toggle.setCompanyId(null);
            toggle.setCode(code);
            toggle.setLabel(firstNonNull(text(node, "label", "question", "name"), code));
            toggle.setDescription(text(node, "description", "notes"));
            Boolean defaultOn = boolOf(node, "default_on", "default");
            toggle.setDefaultOn(defaultOn == null || defaultOn);
            scopeToggleRepository.save(toggle);
            if (isNew) inserted++;
        }
        summary.countInserted("scope_toggle", inserted);
    }

    // ------------------------------------------------------------- recompute

    /**
     * Stores what CPM actually produces for each template, so the library screen can show the
     * computed length next to the published one instead of quoting a figure the engine will
     * not deliver.
     */
    private void recomputeTemplateLengths(SeedImportSummary summary) {
        WorkingCalendar calendar = WorkingCalendar.uaeDefault();
        for (ScheduleTemplate template : templateRepository.findSystemTemplates()) {
            ScheduleParameters params = new ScheduleParameters();
            params.setStartDate(LocalDate.now());
            TemplatePlan plan = planner.plan(template, params, calendar);
            int computed = plan.getResult().getTotalWorkingDays();
            template.setComputedWorkingDays(computed);

            if (template.getTargetWorkingDays() != null && template.getTargetWorkingDays() != computed) {
                String note = String.format(
                        "Published as %d working days; CPM over the seed's own predecessor logic computes %d. "
                                + "The seed's start_wd/finish_wd columns are stored but not used for scheduling.",
                        template.getTargetWorkingDays(), computed);
                template.setDataQualityNotes(note);
                summary.warn("Template " + template.getCode() + ": " + note);
            }
            templateRepository.save(template);
        }
    }

    // ----------------------------------------------------------------- utils

    private String baseParametersJson(JsonNode node) {
        Map<String, Object> base = new LinkedHashMap<>();
        Double area = doubleOf(node, "base_area_sqft", "area_sqft", "reference_area_sqft");
        if (area != null) base.put("areaSqft", area);
        Integer rooms = intOf(node, "base_room_count", "room_count", "rooms");
        if (rooms != null) base.put("roomCount", rooms);
        Integer floors = intOf(node, "base_floor_count", "floor_count", "floors");
        if (floors != null) base.put("floorCount", floors);
        Integer bedrooms = intOf(node, "base_bedrooms", "bedrooms", "bedroom_count");
        if (bedrooms != null) base.put("bedrooms", bedrooms);
        Integer bathrooms = intOf(node, "base_bathrooms", "bathrooms", "bathroom_count");
        if (bathrooms != null) base.put("bathrooms", bathrooms);
        Integer kitchens = intOf(node, "base_kitchens", "kitchens", "kitchen_count");
        if (kitchens != null) base.put("kitchens", kitchens);
        String finish = text(node, "base_finish_level", "finish_level", "specification");
        if (finish != null) base.put("finishLevel", finish);
        Integer crews = intOf(node, "base_crew_count", "crew_count", "crews");
        if (crews != null) base.put("crewCount", crews);
        return base.isEmpty() ? null : writeJson(base);
    }

    /** Words worth matching an activity name against, minus the noise. */
    private String keywordsFrom(String name, String explicit) {
        if (explicit != null) return explicit;
        Set<String> words = new java.util.LinkedHashSet<>();
        for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() >= 4) words.add(word);
        }
        return words.isEmpty() ? name.toLowerCase(Locale.ROOT) : String.join(",", words);
    }

    private String normaliseToggle(String raw) {
        String value = SeedValueParser.trimToNull(raw);
        if (value == null) return null;
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_)|(_$)", "");
    }

    private JsonNode section(JsonNode root, String... names) {
        if (root == null) return null;
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && !node.isNull()) return node;
        }
        return null;
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && value.isValueNode()) {
                String trimmed = SeedValueParser.trimToNull(value.asText());
                if (trimmed != null) return trimmed;
            }
        }
        return null;
    }

    private Integer intOf(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) continue;
            if (value.isNumber()) return value.asInt();
            Integer parsed = SeedValueParser.minOf(value.asText());
            if (parsed != null) return parsed;
        }
        return null;
    }

    private Double doubleOf(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) continue;
            if (value.isNumber()) return value.asDouble();
            Integer parsed = SeedValueParser.minOf(value.asText());
            if (parsed != null) return parsed.doubleValue();
        }
        return null;
    }

    private Boolean boolOf(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) continue;
            if (value.isBoolean()) return value.asBoolean();
            return SeedValueParser.isYes(value.asText());
        }
        return null;
    }

    private List<String> stringList(JsonNode node, String... names) {
        List<String> out = new ArrayList<>();
        JsonNode value = section(node, names);
        if (value == null) return out;
        if (value.isArray()) {
            for (JsonNode item : value) {
                String text = SeedValueParser.trimToNull(item.asText());
                if (text != null) out.add(text);
            }
        } else {
            out.addAll(SeedValueParser.splitList(value.asText()));
        }
        return out;
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
