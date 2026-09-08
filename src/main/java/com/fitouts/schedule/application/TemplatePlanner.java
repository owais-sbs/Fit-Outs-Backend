package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fitouts.schedule.domain.LockedConstraintRule;
import com.fitouts.schedule.domain.LockedConstraintRuleRepository;
import com.fitouts.schedule.domain.ProductivityNorm;
import com.fitouts.schedule.domain.ProductivityNormRepository;
import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.TemplateActivity;
import com.fitouts.schedule.domain.TemplateActivityRepository;
import com.fitouts.schedule.domain.TemplateDependency;
import com.fitouts.schedule.domain.TemplateDependencyRepository;
import com.fitouts.schedule.domain.TemplateProcurementItem;
import com.fitouts.schedule.domain.TemplateProcurementItemRepository;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.CpmEngine;
import com.fitouts.schedule.engine.CpmLink;
import com.fitouts.schedule.engine.DependencyType;
import com.fitouts.schedule.engine.DurationScaler;
import com.fitouts.schedule.engine.ScheduleParameters;
import com.fitouts.schedule.engine.WorkingCalendar;
import com.fitouts.shared.context.CompanyContext;

import lombok.RequiredArgsConstructor;

/**
 * Turns a stored template plus a set of parameters into a solved programme.
 *
 * <p>Pure: nothing here writes to the database. Preview calls it and renders the result; apply
 * calls it and persists the result. That is deliberate, so what the PM approved on screen is
 * byte-for-byte what gets written.
 */
@Component
@RequiredArgsConstructor
public class TemplatePlanner {

    private final TemplateActivityRepository activityRepository;
    private final TemplateDependencyRepository dependencyRepository;
    private final TemplateProcurementItemRepository procurementRepository;
    private final ProductivityNormRepository productivityNormRepository;
    private final LockedConstraintRuleRepository lockedConstraintRepository;
    private final DurationScaler durationScaler;
    private final CpmEngine cpmEngine;

    public TemplatePlan plan(ScheduleTemplate template, ScheduleParameters params, WorkingCalendar calendar) {
        TemplatePlan plan = new TemplatePlan();
        plan.setTemplate(template);
        plan.setParameters(params);

        List<TemplateActivity> templateActivities =
                activityRepository.findByTemplateUuidOrderBySortOrderAsc(template.getUuid());
        List<TemplateDependency> templateDependencies =
                dependencyRepository.findByTemplateUuid(template.getUuid());
        List<ProductivityNorm> norms = productivityNormRepository.findVisible(CompanyContext.get());
        List<LockedConstraintRule> lockRules = lockedConstraintRepository.findVisible(CompanyContext.get());

        Set<String> includedCodes = new HashSet<>();
        int excluded = 0;

        for (TemplateActivity ta : templateActivities) {
            if (!params.toggleOn(ta.getScopeToggleCode())) {
                excluded++;
                continue;
            }
            includedCodes.add(ta.getActivityCode());
            plan.getTemplateActivities().put(ta.getActivityCode(), ta);

            DurationScaler.Scaled scaled = durationScaler.scale(ta, params, norms);
            plan.getScaling().put(ta.getActivityCode(), scaled);

            CpmActivity activity = new CpmActivity();
            activity.setCode(ta.getActivityCode());
            activity.setName(ta.getName());
            activity.setWbsPhase(ta.getWbsPhase());
            activity.setTradePackageCode(ta.getTradePackageCode());
            activity.setDurationWorkingDays(scaled.durationDays());
            activity.setMilestone(ta.isMilestone());
            activity.setLockedDuration(ta.isLockedDuration() || "FIXED".equals(scaled.methodUsed()));
            activity.setConstraintNote(ta.getConstraintNote());
            activity.setRef(ta);
            plan.getActivities().add(activity);
        }
        plan.setExcludedByToggleCount(excluded);

        for (TemplateDependency td : templateDependencies) {
            // A toggled-off activity takes its links with it, otherwise the network dangles.
            if (!includedCodes.contains(td.getPredecessorCode())
                    || !includedCodes.contains(td.getSuccessorCode())) {
                continue;
            }
            CpmLink link = new CpmLink();
            link.setPredecessorCode(td.getPredecessorCode());
            link.setSuccessorCode(td.getSuccessorCode());
            link.setType(DependencyType.from(td.getType()));
            link.setLagWorkingDays(td.getLagDays());
            link.setLocked(td.isLocked() || matchesLockRule(td, plan, lockRules));
            link.setLockReason(td.getLockReason() != null ? td.getLockReason() : lockReasonFor(td, plan, lockRules));
            plan.getLinks().add(link);
        }

        LocalDate start = params.getStartDate() == null ? LocalDate.now() : params.getStartDate();
        plan.setResult(cpmEngine.solve(plan.getActivities(), plan.getLinks(), start, calendar));
        plan.getWarnings().addAll(plan.getResult().getWarnings());

        flagIncompleteLogic(plan, calendar);
        buildOrderByLines(plan, template, calendar);
        return plan;
    }

    // ------------------------------------------------------------- order-by

    /**
     * Backward-schedules each long-lead item from the start of the activity that installs it.
     * Lead times are calendar days: suppliers do not observe the site's working week.
     */
    private void buildOrderByLines(TemplatePlan plan, ScheduleTemplate template, WorkingCalendar calendar) {
        List<TemplateProcurementItem> items = new ArrayList<>(
                procurementRepository.findByTemplateUuidOrderBySortOrderAsc(template.getUuid()));
        items.addAll(procurementRepository.findByTemplateUuidIsNull());
        if (items.isEmpty()) return;

        LocalDate today = LocalDate.now();
        Map<String, CpmActivity> byCode = new LinkedHashMap<>();
        for (CpmActivity a : plan.getActivities()) byCode.put(a.getCode(), a);

        Set<String> seen = new HashSet<>();
        for (TemplateProcurementItem item : items) {
            if (!seen.add(item.getItemName().toLowerCase(Locale.ROOT))) continue;

            CpmActivity install = resolveInstallActivity(item, byCode, plan);
            if (install == null || install.getEarlyStart() == null) {
                plan.getWarnings().add("Long-lead item '" + item.getItemName()
                        + "' has no matching install activity in this template, so no order-by date was computed.");
                continue;
            }
            int lead = item.planningLeadDays();
            if (lead <= 0) {
                plan.getWarnings().add("Long-lead item '" + item.getItemName()
                        + "' has no lead time recorded; its order-by date is the install start.");
            }

            LocalDate orderBy = calendar.previousWorkingDay(install.getEarlyStart().minusDays(lead));
            boolean overdue = orderBy.isBefore(today);

            TemplatePlan.OrderByLine line = new TemplatePlan.OrderByLine();
            line.setItemName(item.getItemName());
            line.setLeadTimeCalendarDays(lead);
            line.setInstallActivityCode(install.getCode());
            line.setInstallStartDate(install.getEarlyStart());
            line.setOrderByDate(orderBy);
            line.setOverdue(overdue);
            line.setRiskNote(item.getRiskNote());
            line.setSiteInfoNeeded(item.getSiteInfoNeeded());
            plan.getOrderByLines().add(line);

            if (overdue) {
                plan.getBlockers().add(String.format(
                        "%s had to be ordered by %s (%d-day lead for %s starting %s). "
                                + "That date has passed, so this start date is not achievable.",
                        item.getItemName(), orderBy, lead, install.getName(), install.getEarlyStart()));
            }
        }
        plan.getOrderByLines().sort(java.util.Comparator.comparing(TemplatePlan.OrderByLine::getOrderByDate));
    }

    private CpmActivity resolveInstallActivity(TemplateProcurementItem item,
                                               Map<String, CpmActivity> byCode, TemplatePlan plan) {
        if (item.getLinkedInstallActivityCode() != null) {
            CpmActivity direct = byCode.get(item.getLinkedInstallActivityCode());
            if (direct != null) return direct;
        }
        if (item.getMatchKeywords() != null) {
            for (String keyword : item.getMatchKeywords().split(",")) {
                String k = keyword.trim().toLowerCase(Locale.ROOT);
                if (k.isEmpty()) continue;
                for (CpmActivity a : plan.getActivities()) {
                    if (a.getName() != null && a.getName().toLowerCase(Locale.ROOT).contains(k)) return a;
                }
            }
        }
        // Last resort: the item name itself against activity names.
        String name = item.getItemName().toLowerCase(Locale.ROOT);
        for (CpmActivity a : plan.getActivities()) {
            if (a.getName() != null && a.getName().toLowerCase(Locale.ROOT).contains(name)) return a;
        }
        return null;
    }

    // ------------------------------------------------------------- data quality

    /**
     * The source file gives each activity a start day number as well as predecessors. Where
     * pure CPM puts an activity far earlier than the source intended, the predecessor list is
     * incomplete rather than the engine being wrong. Boundary walls and swimming pools are the
     * known cases. We surface them instead of inventing the missing logic.
     */
    private void flagIncompleteLogic(TemplatePlan plan, WorkingCalendar calendar) {
        LocalDate projectStart = plan.getResult().getProjectStart();
        if (projectStart == null) return;

        for (CpmActivity a : plan.getActivities()) {
            TemplateActivity ta = plan.getTemplateActivities().get(a.getCode());
            if (ta == null || ta.getSeedStartWd() == null || a.getEarlyStart() == null) continue;

            int computedStartWd = calendar.workingDaysBetweenInclusive(projectStart, a.getEarlyStart());
            int drift = ta.getSeedStartWd() - computedStartWd;
            if (drift >= 30) {
                plan.getWarnings().add(String.format(
                        "%s (%s) schedules on working day %d but the source template places it on day %d. "
                                + "Its predecessor logic is incomplete, so it will look early on the Gantt.",
                        a.getCode(), a.getName(), computedStartWd, ta.getSeedStartWd()));
            }
        }
    }

    // ---------------------------------------------------------------- locking

    private boolean matchesLockRule(TemplateDependency td, TemplatePlan plan, List<LockedConstraintRule> rules) {
        return findLockRule(td, plan, rules) != null;
    }

    private String lockReasonFor(TemplateDependency td, TemplatePlan plan, List<LockedConstraintRule> rules) {
        LockedConstraintRule rule = findLockRule(td, plan, rules);
        return rule == null ? null : rule.getReason();
    }

    /**
     * A lag between two activities is locked when it corresponds to a physical hold: curing,
     * a flood test, concrete strength gain. Matched on the predecessor's name against the
     * rule's keywords, because the seed carries no explicit link.
     */
    private LockedConstraintRule findLockRule(TemplateDependency td, TemplatePlan plan,
                                              List<LockedConstraintRule> rules) {
        if (td.getLagDays() <= 0 || rules.isEmpty()) return null;
        TemplateActivity pred = plan.getTemplateActivities().get(td.getPredecessorCode());
        if (pred == null || pred.getName() == null) return null;
        String haystack = pred.getName().toLowerCase(Locale.ROOT);

        for (LockedConstraintRule rule : rules) {
            if (rule.isCompressible() || rule.getMatchKeywords() == null) continue;
            for (String keyword : rule.getMatchKeywords().split(",")) {
                String k = keyword.trim().toLowerCase(Locale.ROOT);
                if (!k.isEmpty() && haystack.contains(k)) return rule;
            }
        }
        return null;
    }
}
