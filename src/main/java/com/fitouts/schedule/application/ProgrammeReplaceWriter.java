package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fitouts.project.domain.Project;
import com.fitouts.schedule.api.ScheduleApplyResponse;
import com.fitouts.schedule.api.SchedulePreviewResponse;
import com.fitouts.schedule.domain.BoqMatchSource;
import com.fitouts.schedule.domain.ProjectSchedule;
import com.fitouts.schedule.domain.ProjectScheduleRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityBoqLine;
import com.fitouts.schedule.domain.ScheduleActivityBoqLineRepository;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.domain.ScheduleDependency;
import com.fitouts.schedule.domain.ScheduleDependencyRepository;
import com.fitouts.schedule.domain.SchedulePublishStatus;
import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.TemplateActivity;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.CpmLink;
import com.fitouts.schedule.engine.WorkingCalendar;

import lombok.RequiredArgsConstructor;

/**
 * Shared bulk replace of a project's programme (activities + deps), then cascade.
 * Used by template apply and BOQ / Blend programme modes.
 */
@Component
@RequiredArgsConstructor
public class ProgrammeReplaceWriter {

    private final ScheduleActivityRepository activityRepository;
    private final ScheduleDependencyRepository dependencyRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final ScheduleActivityBoqLineRepository activityBoqLineRepository;
    private final ScheduleApplyCascade cascade;

    public record Attachment(
            String activityCode,
            UUID boqLineId,
            BoqMatchSource matchSource
    ) {}

    public record WriteRequest(
            Project project,
            UUID companyId,
            Long createdByAccountId,
            TemplatePlan plan,
            WorkingCalendar calendar,
            UUID workCalendarUuid,
            ScheduleTemplate template,
            String templateCodeOverride,
            String parametersJson,
            String togglesJson,
            String fastTrackAckJson,
            SchedulePreviewResponse preview,
            /** Activity code → BOQ line id for Mode 2 / unmatched Mode 3 activities. */
            Map<String, UUID> boqLineIdByActivityCode,
            List<Attachment> attachments
    ) {}

    public ScheduleApplyResponse write(WriteRequest req) {
        Long projectId = req.project().getId();
        UUID companyId = req.companyId();
        TemplatePlan plan = req.plan();

        List<ScheduleActivity> existing = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        int replaced = existing.size();

        Map<String, Integer> priorProgress = new HashMap<>();
        for (ScheduleActivity a : existing) {
            if (a.getActivityCode() != null) {
                priorProgress.put(a.getActivityCode(), a.getPercentComplete());
            }
        }

        activityBoqLineRepository.deleteByProjectIdAndCompanyId(projectId, companyId);
        for (ScheduleActivity a : existing) {
            dependencyRepository.deleteByPredecessorUuidOrSuccessorUuid(a.getUuid(), a.getUuid());
        }
        activityRepository.deleteAll(existing);
        activityRepository.flush();

        Map<String, UUID> boqByCode = req.boqLineIdByActivityCode() != null
                ? req.boqLineIdByActivityCode() : Map.of();

        Map<String, ScheduleActivity> written = new LinkedHashMap<>();
        int order = 0;
        for (CpmActivity ca : plan.getActivities()) {
            TemplateActivity ta = plan.getTemplateActivities().get(ca.getCode());
            ScheduleActivity a = new ScheduleActivity();
            a.setProjectId(projectId);
            a.setCompanyId(companyId);
            a.setName(ca.getName());
            a.setActivityCode(ca.getCode());
            a.setWbsPhase(ca.getWbsPhase());
            a.setTradePackageCode(ca.getTradePackageCode());
            a.setStartDate(ca.getEarlyStart());
            a.setEndDate(ca.getEarlyFinish());
            a.setEarlyStart(ca.getEarlyStart());
            a.setEarlyFinish(ca.getEarlyFinish());
            a.setLateStart(ca.getLateStart());
            a.setLateFinish(ca.getLateFinish());
            a.setTotalFloat(ca.getTotalFloat());
            a.setFreeFloat(ca.getFreeFloat());
            a.setCritical(ca.isCritical());
            a.setDurationWorkingDays(ca.effectiveDuration());
            a.setMilestone(ca.isMilestone());
            a.setLockedDuration(ca.isLockedDuration());
            a.setConstraintNote(ca.getConstraintNote());
            a.setScalingMethod(plan.getScaling().containsKey(ca.getCode())
                    ? plan.getScaling().get(ca.getCode()).methodUsed() : null);
            a.setPercentComplete(priorProgress.getOrDefault(ca.getCode(), 0));
            a.setPublishStatus(SchedulePublishStatus.DRAFT);
            a.setSortOrder(order++);
            a.setCreatedBy(req.createdByAccountId());
            a.setBoqLineId(boqByCode.get(ca.getCode()));
            if (ta != null && ta.isMilestone()) {
                a.setWeight(java.math.BigDecimal.ZERO);
            }
            written.put(ca.getCode(), activityRepository.save(a));
        }

        int dependencyCount = 0;
        for (CpmLink link : plan.getLinks()) {
            ScheduleActivity pred = written.get(link.getPredecessorCode());
            ScheduleActivity succ = written.get(link.getSuccessorCode());
            if (pred == null || succ == null) continue;
            ScheduleDependency dep = new ScheduleDependency();
            dep.setProjectId(projectId);
            dep.setCompanyId(companyId);
            dep.setPredecessorUuid(pred.getUuid());
            dep.setSuccessorUuid(succ.getUuid());
            dep.setDependencyType(link.getType().name());
            dep.setLagWorkingDays(link.getLagWorkingDays());
            dep.setLocked(link.isLocked());
            dep.setLockReason(link.getLockReason());
            dependencyRepository.save(dep);
            dependencyCount++;
        }

        if (req.attachments() != null) {
            for (Attachment att : req.attachments()) {
                ScheduleActivity parent = written.get(att.activityCode());
                if (parent == null || att.boqLineId() == null) continue;
                ScheduleActivityBoqLine row = new ScheduleActivityBoqLine();
                row.setScheduleActivityUuid(parent.getUuid());
                row.setBoqLineId(att.boqLineId());
                row.setProjectId(projectId);
                row.setCompanyId(companyId);
                row.setMatchSource(att.matchSource() != null ? att.matchSource() : BoqMatchSource.USER_CHANGED);
                activityBoqLineRepository.save(row);
            }
        }

        String templateCode = req.template() != null
                ? req.template().getCode()
                : (StringUtils.hasText(req.templateCodeOverride()) ? req.templateCodeOverride() : "BOQ");

        ProjectSchedule schedule = projectScheduleRepository.findByProjectId(projectId)
                .orElseGet(ProjectSchedule::new);
        schedule.setProjectId(projectId);
        schedule.setCompanyId(companyId);
        if (req.template() != null) {
            schedule.setTemplateUuid(req.template().getUuid());
            schedule.setTemplateVersion(req.template().getVersion());
        } else {
            schedule.setTemplateUuid(null);
            schedule.setTemplateVersion(null);
        }
        schedule.setTemplateCode(templateCode);
        schedule.setParametersJson(req.parametersJson());
        schedule.setTogglesJson(req.togglesJson());
        schedule.setWorkCalendarUuid(req.workCalendarUuid());
        schedule.setDataDate(LocalDate.now());
        schedule.setCurrentFinishDate(plan.finishDate());
        schedule.setComputedWorkingDays(plan.getResult() != null ? plan.getResult().getTotalWorkingDays() : null);
        if (schedule.getBaselineFinishDate() == null) {
            schedule.setBaselineFinishDate(plan.finishDate());
            schedule.setBaselineSavedAt(OffsetDateTime.now());
        }
        if (StringUtils.hasText(req.fastTrackAckJson())) {
            schedule.setFastTrackAckJson(req.fastTrackAckJson());
        }
        schedule.setPublishedBy(req.createdByAccountId());
        schedule.setPublishedAt(OffsetDateTime.now());
        projectScheduleRepository.save(schedule);

        ScheduleApplyCascade.Summary summary = cascade.run(
                req.project(), companyId, plan, written, req.calendar());

        String label = req.template() != null ? req.template().getName() : templateCode;
        return ScheduleApplyResponse.builder()
                .preview(req.preview())
                .activitiesWritten(written.size())
                .dependenciesWritten(dependencyCount)
                .activitiesReplaced(replaced)
                .packagesUpserted(summary.packages)
                .billingMilestonesCreated(summary.billingMilestones)
                .holdPointsCreated(summary.holdPoints)
                .approvalTargetsUpdated(summary.approvalTargets)
                .orderByRowsWritten(summary.orderByRows)
                .note(String.format(
                        "%s applied: %d activities finishing %s (%d working days). "
                                + "%d packages, %d billing drafts, %d hold points and %d order-by dates written.",
                        label, written.size(), plan.finishDate(),
                        plan.getResult() != null ? plan.getResult().getTotalWorkingDays() : 0,
                        summary.packages.size(),
                        summary.billingMilestones.size(),
                        summary.holdPoints.size(), summary.orderByRows))
                .build();
    }
}
