package com.fitouts.schedule.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fitouts.approval.domain.ApprovalCase;
import com.fitouts.approval.domain.ApprovalCaseRepository;
import com.fitouts.billing.domain.BillingMilestone;
import com.fitouts.billing.domain.BillingMilestoneRepository;
import com.fitouts.billing.domain.BillingStatus;
import com.fitouts.holdpoint.domain.ActivityQualityTemplate;
import com.fitouts.holdpoint.domain.ActivityQualityTemplateRepository;
import com.fitouts.holdpoint.domain.QualityHoldPoint;
import com.fitouts.holdpoint.domain.QualityHoldPointRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.api.SchedulePreviewResponse;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleOrderBy;
import com.fitouts.schedule.domain.ScheduleOrderByRepository;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.WorkingCalendar;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import lombok.RequiredArgsConstructor;

/**
 * The "one action" downstream of an apply: everything that should follow from a published
 * programme, in the same transaction as the CPM commit.
 *
 * <p>Each step upserts rather than recreating, because apply is run repeatedly as the
 * programme is revised, and a PM who has already awarded a package or issued a payment
 * application should not lose that when the dates shift.
 */
@Component
@RequiredArgsConstructor
public class ScheduleApplyCascade {

    private final SubcontractorPackageRepository packageRepository;
    private final BillingMilestoneRepository billingMilestoneRepository;
    private final QualityHoldPointRepository holdPointRepository;
    private final ActivityQualityTemplateRepository qualityTemplateRepository;
    private final ApprovalCaseRepository approvalCaseRepository;
    private final ScheduleOrderByRepository orderByRepository;

    /**
     * @param persistedActivities the live rows just written, keyed by activity code, so the
     *                            downstream records link to real activity UUIDs.
     */
    public Summary run(Project project, UUID companyId, TemplatePlan plan,
                       Map<String, ScheduleActivity> persistedActivities, WorkingCalendar calendar) {
        Summary summary = new Summary();
        summary.packages = upsertPackages(project, companyId, plan, persistedActivities);
        summary.billingMilestones = upsertBillingMilestones(project, companyId, plan, persistedActivities);
        summary.holdPoints = instantiateHoldPoints(project, companyId, plan, persistedActivities);
        summary.approvalTargets = refreshApprovalTargets(project, companyId, plan, persistedActivities, calendar);
        summary.orderByRows = writeOrderByRows(project, companyId, plan, persistedActivities);
        return summary;
    }

    // ------------------------------------------------------------- packages

    /**
     * One shell per template trade, with planned dates spanning that trade's activities.
     * Deliberately trade-level, not BOQ line level: the BOQ stays section-scoped.
     */
    private List<SchedulePreviewResponse.PackagePreview> upsertPackages(
            Project project, UUID companyId, TemplatePlan plan,
            Map<String, ScheduleActivity> persisted) {

        Map<String, List<CpmActivity>> byTrade = new LinkedHashMap<>();
        for (CpmActivity a : plan.getActivities()) {
            if (a.getTradePackageCode() == null || a.getTradePackageCode().isBlank()) continue;
            byTrade.computeIfAbsent(a.getTradePackageCode(), k -> new ArrayList<>()).add(a);
        }
        if (byTrade.isEmpty()) return List.of();

        Map<String, SubcontractorPackage> existing = new HashMap<>();
        for (SubcontractorPackage p : packageRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId)) {
            if (p.getTradePackageCode() != null) existing.putIfAbsent(p.getTradePackageCode(), p);
        }

        List<SchedulePreviewResponse.PackagePreview> previews = new ArrayList<>();
        for (Map.Entry<String, List<CpmActivity>> entry : byTrade.entrySet()) {
            String tradeCode = entry.getKey();
            List<CpmActivity> activities = entry.getValue();

            LocalDate start = activities.stream().map(CpmActivity::getEarlyStart)
                    .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
            LocalDate finish = activities.stream().map(CpmActivity::getEarlyFinish)
                    .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);

            SubcontractorPackage pkg = existing.get(tradeCode);
            if (pkg == null) {
                pkg = new SubcontractorPackage();
                pkg.setProjectId(project.getId());
                pkg.setCompanyId(companyId);
                pkg.setTradePackageCode(tradeCode);
            }
            pkg.setName(packageName(tradeCode, activities));
            pkg.setPlannedStart(start);
            pkg.setPlannedFinish(finish);
            pkg.setActivityCodes(String.join(",", activities.stream().map(CpmActivity::getCode).toList()));
            packageRepository.save(pkg);

            previews.add(SchedulePreviewResponse.PackagePreview.builder()
                    .tradePackageCode(tradeCode)
                    .name(pkg.getName())
                    .plannedStart(start)
                    .plannedFinish(finish)
                    .activityCount(activities.size())
                    .build());
        }
        return previews;
    }

    private String packageName(String tradeCode, List<CpmActivity> activities) {
        String label = activities.stream()
                .map(a -> a.getRef() instanceof com.fitouts.schedule.domain.TemplateActivity ta
                        ? ta.getTradeLabel() : null)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        return label != null ? label : tradeCode;
    }

    // -------------------------------------------------------------- billing

    /**
     * Creates the template's payment gates and links each to the activity that triggers it.
     * Amounts come from the project budget; a project without one gets zero-value placeholders
     * rather than nothing, because the gate structure is still useful.
     */
    private List<String> upsertBillingMilestones(Project project, UUID companyId, TemplatePlan plan,
                                                 Map<String, ScheduleActivity> persisted) {
        List<BillingMilestonePresets.Milestone> presets =
                BillingMilestonePresets.forTemplate(plan.getTemplate().getCode());

        Map<String, BillingMilestone> existingByName = new HashMap<>();
        for (BillingMilestone m : billingMilestoneRepository
                .findByProjectIdAndCompanyIdOrderByDueDateAscCreatedAtAsc(project.getId(), companyId)) {
            existingByName.putIfAbsent(m.getName().toLowerCase(Locale.ROOT), m);
        }

        BigDecimal contractValue = project.getBudget() == null ? BigDecimal.ZERO : project.getBudget();
        List<String> created = new ArrayList<>();

        for (BillingMilestonePresets.Milestone preset : presets) {
            BillingMilestone milestone = existingByName.get(preset.name().toLowerCase(Locale.ROOT));
            boolean isNew = milestone == null;
            if (isNew) {
                milestone = new BillingMilestone();
                milestone.setProjectId(project.getId());
                milestone.setCompanyId(companyId);
                milestone.setName(preset.name());
                milestone.setStatus(BillingStatus.DRAFT);
            } else if (milestone.getStatus() != BillingStatus.DRAFT) {
                // Already claimed or invoiced. Reschedule nothing; the commercial position stands.
                continue;
            }

            milestone.setAmount(contractValue.multiply(BigDecimal.valueOf(preset.paymentPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            if (preset.percentCompleteRequired() != null) {
                milestone.setPercentCompleteRequired(BigDecimal.valueOf(preset.percentCompleteRequired()));
            }

            CpmActivity trigger = BillingMilestonePresets.match(plan.getActivities(), preset.activityKeywords());
            if (trigger != null) {
                ScheduleActivity live = persisted.get(trigger.getCode());
                if (live != null) milestone.setLinkedActivityUuid(live.getUuid());
                milestone.setDueDate(trigger.getEarlyFinish());
            } else if (preset.retention()) {
                // Retention falls due after the DLP, not on a programme gate.
                LocalDate finish = plan.finishDate();
                milestone.setDueDate(finish == null ? null : finish.plusMonths(12));
            }

            billingMilestoneRepository.save(milestone);
            if (isNew) created.add(preset.name());
        }
        return created;
    }

    // ----------------------------------------------------------- hold points

    /**
     * Instantiates quality holds on activities that carry a locked constraint: flood tests,
     * moisture readings, curing. These are the points where proceeding without a signature
     * costs a rip-out, so the hold exists on the programme from day one.
     */
    private List<String> instantiateHoldPoints(Project project, UUID companyId, TemplatePlan plan,
                                               Map<String, ScheduleActivity> persisted) {
        Map<String, ActivityQualityTemplate> templates = new HashMap<>();
        for (ActivityQualityTemplate t : qualityTemplateRepository.findByCompanyIdOrderByActivityTypeAsc(companyId)) {
            if (t.getActivityType() != null) {
                templates.put(t.getActivityType().toLowerCase(Locale.ROOT), t);
            }
        }

        Map<UUID, QualityHoldPoint> existingByActivity = new HashMap<>();
        for (QualityHoldPoint hp : holdPointRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId)) {
            if (hp.getActivityUuid() != null) existingByActivity.putIfAbsent(hp.getActivityUuid(), hp);
        }

        List<String> created = new ArrayList<>();
        for (CpmActivity a : plan.getActivities()) {
            if (!a.isLockedDuration() && a.getConstraintNote() == null) continue;

            ScheduleActivity live = persisted.get(a.getCode());
            if (live == null || existingByActivity.containsKey(live.getUuid())) continue;

            QualityHoldPoint hp = new QualityHoldPoint();
            hp.setProjectId(project.getId());
            hp.setCompanyId(companyId);
            hp.setActivityUuid(live.getUuid());
            hp.setTitle("Hold point — " + a.getName());
            hp.setNotes(a.getConstraintNote());

            ActivityQualityTemplate matched = matchQualityTemplate(a, templates);
            if (matched != null) {
                hp.setActivityType(matched.getActivityType());
                hp.setChecklistJson(matched.getChecklistJson());
            }
            holdPointRepository.save(hp);
            created.add(a.getCode());
        }
        return created;
    }

    private ActivityQualityTemplate matchQualityTemplate(CpmActivity activity,
                                                         Map<String, ActivityQualityTemplate> templates) {
        if (templates.isEmpty() || activity.getName() == null) return null;
        String name = activity.getName().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ActivityQualityTemplate> entry : templates.entrySet()) {
            if (name.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    // ------------------------------------------------------------- approvals

    /**
     * Aligns existing approval cases with the new programme: each case that blocks activities
     * gets a target submission date computed backwards from the earliest activity it blocks,
     * less the authority's SLA. Cases with no blocking link are left alone.
     */
    private List<SchedulePreviewResponse.ApprovalTargetPreview> refreshApprovalTargets(
            Project project, UUID companyId, TemplatePlan plan,
            Map<String, ScheduleActivity> persisted, WorkingCalendar calendar) {

        List<ApprovalCase> cases = approvalCaseRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtAsc(project.getId(), companyId);
        if (cases.isEmpty()) return List.of();

        LocalDate today = LocalDate.now();
        List<SchedulePreviewResponse.ApprovalTargetPreview> previews = new ArrayList<>();

        for (ApprovalCase c : cases) {
            List<String> blockedCodes = splitCodes(c.getBlocksActivityCodes());
            CpmActivity earliest = null;
            for (String code : blockedCodes) {
                CpmActivity a = plan.activity(code);
                if (a == null || a.getEarlyStart() == null) continue;
                if (earliest == null || a.getEarlyStart().isBefore(earliest.getEarlyStart())) earliest = a;
            }
            if (earliest == null) continue;

            int sla = c.getSlaDays() == null ? 0 : c.getSlaDays();
            LocalDate requiredApproval = calendar.previousWorkingDay(earliest.getEarlyStart().minusDays(1));
            LocalDate targetSubmission = calendar.subtractWorkingDays(requiredApproval, Math.max(sla, 0));

            c.setTargetSubmissionDate(targetSubmission);
            c.setLinkedActivityUuids(linkedUuids(blockedCodes, persisted));
            approvalCaseRepository.save(c);

            previews.add(SchedulePreviewResponse.ApprovalTargetPreview.builder()
                    .permitTypeCode(c.getPermitTypeCode())
                    .permitTypeName(c.getPermitTypeName())
                    .authorityName(c.getAuthorityName())
                    .slaWorkingDays(c.getSlaDays())
                    .targetSubmissionDate(targetSubmission)
                    .requiredApprovalDate(requiredApproval)
                    .blocksActivityCode(earliest.getCode())
                    .atRisk(targetSubmission.isBefore(today) && !c.getStatus().isTerminal())
                    .build());
        }
        return previews;
    }

    private String linkedUuids(List<String> codes, Map<String, ScheduleActivity> persisted) {
        List<String> uuids = new ArrayList<>();
        for (String code : codes) {
            ScheduleActivity a = persisted.get(code);
            if (a != null) uuids.add(a.getUuid().toString());
        }
        return uuids.isEmpty() ? null : String.join(",", uuids);
    }

    private List<String> splitCodes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> codes = new ArrayList<>();
        for (String part : raw.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) codes.add(trimmed);
        }
        return codes;
    }

    // -------------------------------------------------------------- order-by

    private int writeOrderByRows(Project project, UUID companyId, TemplatePlan plan,
                                 Map<String, ScheduleActivity> persisted) {
        orderByRepository.deleteByProjectId(project.getId());
        for (TemplatePlan.OrderByLine line : plan.getOrderByLines()) {
            ScheduleOrderBy row = new ScheduleOrderBy();
            row.setProjectId(project.getId());
            row.setCompanyId(companyId);
            row.setItemName(line.getItemName());
            row.setLeadTimeCalendarDays(line.getLeadTimeCalendarDays());
            row.setInstallActivityCode(line.getInstallActivityCode());
            row.setInstallStartDate(line.getInstallStartDate());
            row.setOrderByDate(line.getOrderByDate());
            row.setOverdue(line.isOverdue());
            row.setRiskNote(line.getRiskNote());
            row.setSiteInfoNeeded(line.getSiteInfoNeeded());
            Optional.ofNullable(persisted.get(line.getInstallActivityCode()))
                    .ifPresent(a -> row.setInstallActivityUuid(a.getUuid()));
            orderByRepository.save(row);
        }
        return plan.getOrderByLines().size();
    }

    /** What the cascade did, for the apply response. */
    public static class Summary {
        public List<SchedulePreviewResponse.PackagePreview> packages = List.of();
        public List<String> billingMilestones = List.of();
        public List<String> holdPoints = List.of();
        public List<SchedulePreviewResponse.ApprovalTargetPreview> approvalTargets = List.of();
        public int orderByRows;
    }
}
