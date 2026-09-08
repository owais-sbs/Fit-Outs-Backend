package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.api.OrderByResponse;
import com.fitouts.schedule.api.ScheduleApplyResponse;
import com.fitouts.schedule.api.SchedulePreviewRequest;
import com.fitouts.schedule.api.SchedulePreviewResponse;
import com.fitouts.schedule.api.ScheduleTemplateDetailResponse;
import com.fitouts.schedule.api.ScheduleTemplateResponse;
import com.fitouts.schedule.domain.ProjectSchedule;
import com.fitouts.schedule.domain.ProjectScheduleRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.domain.ScheduleDependency;
import com.fitouts.schedule.domain.ScheduleDependencyRepository;
import com.fitouts.schedule.domain.ScheduleOrderBy;
import com.fitouts.schedule.domain.ScheduleOrderByRepository;
import com.fitouts.schedule.domain.ScheduleTemplate;
import com.fitouts.schedule.domain.ScheduleTemplateRepository;
import com.fitouts.schedule.domain.SchedulePublishStatus;
import com.fitouts.schedule.domain.TemplateActivity;
import com.fitouts.schedule.domain.TemplateActivityRepository;
import com.fitouts.schedule.domain.TemplateDependency;
import com.fitouts.schedule.domain.TemplateDependencyRepository;
import com.fitouts.schedule.domain.TemplateProcurementItem;
import com.fitouts.schedule.domain.TemplateProcurementItemRepository;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.CpmLink;
import com.fitouts.schedule.engine.ScheduleParameters;
import com.fitouts.schedule.engine.WorkingCalendar;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Template library plus the preview/apply pipeline that turns a template into a live
 * programme.
 *
 * <p>Preview and apply share {@link TemplatePlanner}, so the dates a PM approves on screen are
 * produced by the same code that writes them.
 */
@Service
@RequiredArgsConstructor
public class ScheduleTemplateService {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> JSON_LIST = new TypeReference<>() {};

    private final ScheduleTemplateRepository templateRepository;
    private final TemplateActivityRepository templateActivityRepository;
    private final TemplateDependencyRepository templateDependencyRepository;
    private final TemplateProcurementItemRepository procurementRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ScheduleDependencyRepository dependencyRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final ScheduleOrderByRepository orderByRepository;
    private final TemplatePlanner planner;
    private final WorkCalendarService workCalendarService;
    private final ScheduleApplyCascade cascade;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------- library

    @Transactional(readOnly = true)
    public List<ScheduleTemplateResponse> listTemplates() {
        UUID companyId = CompanyContext.get();
        Map<String, ScheduleTemplate> byCode = new LinkedHashMap<>();
        // A tenant template with the same code shadows the system one.
        for (ScheduleTemplate t : templateRepository.findVisible(companyId)) {
            ScheduleTemplate existing = byCode.get(t.getCode());
            if (existing == null || (existing.getCompanyId() == null && t.getCompanyId() != null)) {
                byCode.put(t.getCode(), t);
            }
        }
        return byCode.values().stream().map(this::toTemplateResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleTemplateDetailResponse getTemplate(UUID templateUuid) {
        ScheduleTemplate template = requireTemplate(templateUuid);
        List<TemplateActivity> activities =
                templateActivityRepository.findByTemplateUuidOrderBySortOrderAsc(templateUuid);
        List<TemplateDependency> dependencies =
                templateDependencyRepository.findByTemplateUuid(templateUuid);
        List<TemplateProcurementItem> items =
                procurementRepository.findByTemplateUuidOrderBySortOrderAsc(templateUuid);

        // Solve the template at its own base parameters so the admin screen can show what the
        // logic actually produces next to what the source file claims.
        ScheduleParameters baseParams = baseParameters(template);
        baseParams.setStartDate(LocalDate.now());
        WorkingCalendar calendar = workCalendarService.resolve(null);
        TemplatePlan plan = planner.plan(template, baseParams, calendar);
        LocalDate projectStart = plan.getResult().getProjectStart();

        List<ScheduleTemplateDetailResponse.TemplateActivityView> views = new ArrayList<>();
        List<String> incompleteLogic = new ArrayList<>();

        for (TemplateActivity ta : activities) {
            CpmActivity solved = plan.activity(ta.getActivityCode());
            Integer computedStart = null;
            Integer computedFinish = null;
            String variance = null;

            if (solved != null && solved.getEarlyStart() != null) {
                computedStart = calendar.workingDaysBetweenInclusive(projectStart, solved.getEarlyStart());
                computedFinish = calendar.workingDaysBetweenInclusive(projectStart, solved.getEarlyFinish());
                if (ta.getSeedStartWd() != null && !ta.getSeedStartWd().equals(computedStart)) {
                    int drift = ta.getSeedStartWd() - computedStart;
                    variance = drift > 0
                            ? "Source places it " + drift + " working days later than its predecessors require"
                            : "Source places it " + (-drift) + " working days before its predecessor finishes";
                    if (drift >= 30) {
                        incompleteLogic.add(ta.getActivityCode() + " (" + ta.getName()
                                + "): predecessor logic is incomplete, so CPM starts it on day "
                                + computedStart + " instead of the intended day " + ta.getSeedStartWd() + ".");
                    }
                }
            }

            views.add(ScheduleTemplateDetailResponse.TemplateActivityView.builder()
                    .activityCode(ta.getActivityCode())
                    .name(ta.getName())
                    .wbsPhase(ta.getWbsPhase())
                    .tradePackageCode(ta.getTradePackageCode())
                    .tradeLabel(ta.getTradeLabel())
                    .baseDurationDays(ta.getBaseDurationDays())
                    .scalingMethod(ta.getScalingMethod())
                    .scalingDriver(ta.getScalingDriver())
                    .milestone(ta.isMilestone())
                    .lockedDuration(ta.isLockedDuration())
                    .scopeToggleCode(ta.getScopeToggleCode())
                    .constraintNote(ta.getConstraintNote())
                    .seedStartWd(ta.getSeedStartWd())
                    .seedFinishWd(ta.getSeedFinishWd())
                    .computedStartWd(computedStart)
                    .computedFinishWd(computedFinish)
                    .varianceNote(variance)
                    .sortOrder(ta.getSortOrder())
                    .build());
        }

        return ScheduleTemplateDetailResponse.builder()
                .header(toTemplateResponse(template, plan.getResult().getTotalWorkingDays()))
                .activities(views)
                .dependencies(dependencies.stream()
                        .map(d -> SchedulePreviewResponse.PreviewDependency.builder()
                                .predecessorCode(d.getPredecessorCode())
                                .successorCode(d.getSuccessorCode())
                                .type(d.getType())
                                .lagWorkingDays(d.getLagDays())
                                .locked(d.isLocked())
                                .lockReason(d.getLockReason())
                                .build())
                        .toList())
                .procurementItems(items.stream()
                        .map(i -> ScheduleTemplateDetailResponse.ProcurementItemView.builder()
                                .itemName(i.getItemName())
                                .leadTimeCalendarDaysMin(i.getLeadTimeCalendarDaysMin())
                                .leadTimeCalendarDaysMax(i.getLeadTimeCalendarDaysMax())
                                .leadTimeRaw(i.getLeadTimeRaw())
                                .orderByRule(i.getOrderByRule())
                                .siteInfoNeeded(i.getSiteInfoNeeded())
                                .riskNote(i.getRiskNote())
                                .linkedInstallActivityCode(i.getLinkedInstallActivityCode())
                                .build())
                        .toList())
                .incompleteLogicWarnings(incompleteLogic)
                .build();
    }

    // -------------------------------------------------------------- preview

    @Transactional(readOnly = true)
    public SchedulePreviewResponse preview(Long projectId, SchedulePreviewRequest request) {
        requireStaff();
        Project project = requireProject(projectId);
        ScheduleTemplate template = resolveTemplate(request);
        WorkingCalendar calendar = workCalendarService.resolve(request.getWorkCalendarUuid());
        ScheduleParameters params = toParameters(template, request, project);

        TemplatePlan plan = planner.plan(template, params, calendar);
        return toPreview(template, plan, calendar, request);
    }

    // ---------------------------------------------------------------- apply

    /**
     * Writes the programme. Replaces the project's activities and dependencies in one
     * transaction rather than issuing N single writes, then runs the downstream cascade.
     */
    @Transactional
    public ScheduleApplyResponse apply(Long projectId, SchedulePreviewRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        ScheduleTemplate template = resolveTemplate(request);
        WorkingCalendar calendar = workCalendarService.resolve(request.getWorkCalendarUuid());
        ScheduleParameters params = toParameters(template, request, project);

        TemplatePlan plan = planner.plan(template, params, calendar);
        SchedulePreviewResponse preview = toPreview(template, plan, calendar, request);

        if (!preview.getBlockers().isEmpty()) {
            throw new BadRequestException(String.join(" ", preview.getBlockers()));
        }
        if (preview.isRequiresFastTrackAcknowledgement()) {
            throw new BadRequestException(
                    "This fast-track programme cannot be published until all "
                            + preview.getFastTrackConditions().size()
                            + " conditions are acknowledged. Unacknowledged: "
                            + String.join("; ", unacknowledged(template, request)));
        }

        List<ScheduleActivity> existing = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        int replaced = existing.size();

        // Progress already reported against a code is worth keeping across a re-apply.
        Map<String, Integer> priorProgress = new HashMap<>();
        Map<String, UUID> priorUuids = new HashMap<>();
        for (ScheduleActivity a : existing) {
            if (a.getActivityCode() != null) {
                priorProgress.put(a.getActivityCode(), a.getPercentComplete());
                priorUuids.put(a.getActivityCode(), a.getUuid());
            }
        }

        for (ScheduleActivity a : existing) {
            dependencyRepository.deleteByPredecessorUuidOrSuccessorUuid(a.getUuid(), a.getUuid());
        }
        activityRepository.deleteAll(existing);
        activityRepository.flush();

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
            a.setCreatedBy(principal.getAccountId());
            if (ta != null && ta.isMilestone()) a.setWeight(java.math.BigDecimal.ZERO);
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

        ProjectSchedule schedule = projectScheduleRepository.findByProjectId(projectId)
                .orElseGet(ProjectSchedule::new);
        schedule.setProjectId(projectId);
        schedule.setCompanyId(companyId);
        schedule.setTemplateUuid(template.getUuid());
        schedule.setTemplateCode(template.getCode());
        schedule.setTemplateVersion(template.getVersion());
        schedule.setParametersJson(writeJson(request));
        schedule.setTogglesJson(writeJson(request.getScopeToggles()));
        schedule.setWorkCalendarUuid(request.getWorkCalendarUuid());
        schedule.setDataDate(LocalDate.now());
        schedule.setCurrentFinishDate(plan.finishDate());
        schedule.setComputedWorkingDays(plan.getResult().getTotalWorkingDays());
        if (schedule.getBaselineFinishDate() == null) {
            schedule.setBaselineFinishDate(plan.finishDate());
            schedule.setBaselineSavedAt(OffsetDateTime.now());
        }
        if (template.isFastTrack()) {
            schedule.setFastTrackAckJson(writeJson(request.getFastTrackAcknowledgements()));
        }
        schedule.setPublishedBy(principal.getAccountId());
        schedule.setPublishedAt(OffsetDateTime.now());
        projectScheduleRepository.save(schedule);

        ScheduleApplyCascade.Summary summary = cascade.run(project, companyId, plan, written, calendar);

        return ScheduleApplyResponse.builder()
                .preview(preview)
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
                                + "%d packages, %d billing milestones, %d hold points and %d order-by dates written.",
                        template.getName(), written.size(), plan.finishDate(),
                        plan.getResult().getTotalWorkingDays(), summary.packages.size(),
                        summary.billingMilestones.size(), summary.holdPoints.size(), summary.orderByRows))
                .build();
    }

    // ------------------------------------------------------------- order-by

    @Transactional(readOnly = true)
    public List<OrderByResponse> orderByDates(Long projectId) {
        requireProject(projectId);
        LocalDate today = LocalDate.now();
        return orderByRepository.findByProjectIdOrderByOrderByDateAsc(projectId).stream()
                .map(r -> toOrderBy(r, today))
                .toList();
    }

    private OrderByResponse toOrderBy(ScheduleOrderBy r, LocalDate today) {
        return OrderByResponse.builder()
                .uuid(r.getUuid())
                .itemName(r.getItemName())
                .leadTimeCalendarDays(r.getLeadTimeCalendarDays())
                .installActivityCode(r.getInstallActivityCode())
                .installActivityUuid(r.getInstallActivityUuid())
                .installStartDate(r.getInstallStartDate())
                .orderByDate(r.getOrderByDate())
                .overdue(r.getOrderByDate().isBefore(today))
                .daysToOrderBy(ChronoUnit.DAYS.between(today, r.getOrderByDate()))
                .riskNote(r.getRiskNote())
                .siteInfoNeeded(r.getSiteInfoNeeded())
                .build();
    }

    // -------------------------------------------------------------- mapping

    private SchedulePreviewResponse toPreview(ScheduleTemplate template, TemplatePlan plan,
                                              WorkingCalendar calendar, SchedulePreviewRequest request) {
        LocalDate projectStart = plan.getResult().getProjectStart();

        List<SchedulePreviewResponse.PreviewActivity> activities = new ArrayList<>();
        for (CpmActivity a : plan.getActivities()) {
            TemplateActivity ta = plan.getTemplateActivities().get(a.getCode());
            var scaled = plan.getScaling().get(a.getCode());

            String seedVariance = null;
            if (ta != null && ta.getSeedStartWd() != null && a.getEarlyStart() != null) {
                int computed = calendar.workingDaysBetweenInclusive(projectStart, a.getEarlyStart());
                int drift = ta.getSeedStartWd() - computed;
                if (Math.abs(drift) >= 3) {
                    seedVariance = drift > 0
                            ? "Source template starts this " + drift + " working days later"
                            : "Source template starts this " + (-drift) + " working days earlier";
                }
            }

            activities.add(SchedulePreviewResponse.PreviewActivity.builder()
                    .activityCode(a.getCode())
                    .name(a.getName())
                    .wbsPhase(a.getWbsPhase())
                    .tradePackageCode(a.getTradePackageCode())
                    .durationWorkingDays(a.effectiveDuration())
                    .baseDurationWorkingDays(ta == null ? a.effectiveDuration() : ta.getBaseDurationDays())
                    .scalingMethod(scaled == null ? null : scaled.methodUsed())
                    .scalingExplanation(scaled == null ? null : scaled.explanation())
                    .earlyStart(a.getEarlyStart())
                    .earlyFinish(a.getEarlyFinish())
                    .lateStart(a.getLateStart())
                    .lateFinish(a.getLateFinish())
                    .totalFloat(a.getTotalFloat())
                    .freeFloat(a.getFreeFloat())
                    .critical(a.isCritical())
                    .milestone(a.isMilestone())
                    .lockedDuration(a.isLockedDuration())
                    .constraintNote(a.getConstraintNote())
                    .seedVarianceNote(seedVariance)
                    .build());
        }

        List<String> fastTrackConditions = fastTrackConditions(template);
        List<String> stillUnacknowledged = unacknowledged(template, request);

        int workingDays = plan.getResult().getTotalWorkingDays();
        String varianceNote = null;
        if (template.getTargetWorkingDays() != null && template.getTargetWorkingDays() != workingDays) {
            varianceNote = String.format(
                    "This template is published as %d working days but its own dependency logic computes %d. "
                            + "The programme below is the computed figure, because that is what the logic supports.",
                    template.getTargetWorkingDays(), workingDays);
        }

        return SchedulePreviewResponse.builder()
                .templateUuid(template.getUuid())
                .templateCode(template.getCode())
                .templateName(template.getName())
                .startDate(projectStart)
                .finishDate(plan.finishDate())
                .workingDays(workingDays)
                .calendarDays(plan.getResult().getTotalCalendarDays())
                .templateTargetWorkingDays(template.getTargetWorkingDays())
                .targetVarianceNote(varianceNote)
                .activityCount(activities.size())
                .excludedByToggleCount(plan.getExcludedByToggleCount())
                .activities(activities)
                .dependencies(plan.getLinks().stream()
                        .map(l -> SchedulePreviewResponse.PreviewDependency.builder()
                                .predecessorCode(l.getPredecessorCode())
                                .successorCode(l.getSuccessorCode())
                                .type(l.getType().name())
                                .lagWorkingDays(l.getLagWorkingDays())
                                .locked(l.isLocked())
                                .lockReason(l.getLockReason())
                                .build())
                        .toList())
                .criticalPaths(plan.getResult().getCriticalPaths())
                .orderByDates(plan.getOrderByLines().stream()
                        .map(l -> SchedulePreviewResponse.OrderByPreview.builder()
                                .itemName(l.getItemName())
                                .leadTimeCalendarDays(l.getLeadTimeCalendarDays())
                                .installActivityCode(l.getInstallActivityCode())
                                .installStartDate(l.getInstallStartDate())
                                .orderByDate(l.getOrderByDate())
                                .overdue(l.isOverdue())
                                .riskNote(l.getRiskNote())
                                .siteInfoNeeded(l.getSiteInfoNeeded())
                                .build())
                        .toList())
                .approvalTargets(List.of())
                .packages(List.of())
                .warnings(plan.getWarnings())
                .blockers(plan.getBlockers())
                .requiresFastTrackAcknowledgement(!stillUnacknowledged.isEmpty())
                .fastTrackConditions(fastTrackConditions)
                .canPublish(plan.getBlockers().isEmpty() && stillUnacknowledged.isEmpty())
                .build();
    }

    private ScheduleTemplateResponse toTemplateResponse(ScheduleTemplate t) {
        return toTemplateResponse(t, t.getComputedWorkingDays());
    }

    private ScheduleTemplateResponse toTemplateResponse(ScheduleTemplate t, Integer computed) {
        String variance = null;
        if (t.getTargetWorkingDays() != null && computed != null && !computed.equals(t.getTargetWorkingDays())) {
            variance = String.format("Published as %d working days; the template's own logic computes %d.",
                    t.getTargetWorkingDays(), computed);
        }

        Map<String, Object> base = readJsonMap(t.getBaseParametersJson());
        Set<String> toggles = new LinkedHashSet<>();
        for (TemplateActivity ta : templateActivityRepository.findByTemplateUuidOrderBySortOrderAsc(t.getUuid())) {
            if (ta.getScopeToggleCode() != null) toggles.add(ta.getScopeToggleCode());
        }

        return ScheduleTemplateResponse.builder()
                .uuid(t.getUuid())
                .code(t.getCode())
                .name(t.getName())
                .projectType(t.getProjectType())
                .description(t.getDescription())
                .targetWorkingDays(t.getTargetWorkingDays())
                .targetCalendarDays(t.getTargetCalendarDays())
                .computedWorkingDays(computed)
                .varianceNote(variance)
                .systemTemplate(t.isSystemTemplate())
                .fastTrack(t.isFastTrack())
                .fastTrackConditions(fastTrackConditions(t))
                .workWeek(t.getWorkWeek())
                .version(t.getVersion())
                .dataQualityNotes(t.getDataQualityNotes())
                .activityCount(templateActivityRepository.findByTemplateUuidOrderBySortOrderAsc(t.getUuid()).size())
                .dependencyCount(templateDependencyRepository.findByTemplateUuid(t.getUuid()).size())
                .procurementItemCount(procurementRepository.findByTemplateUuidOrderBySortOrderAsc(t.getUuid()).size())
                .scopeToggleCodes(new ArrayList<>(toggles))
                .baseParameters(ScheduleTemplateResponse.BaseParameters.builder()
                        .areaSqft(asDouble(base.get("areaSqft")))
                        .roomCount(asInt(base.get("roomCount")))
                        .floorCount(asInt(base.get("floorCount")))
                        .finishLevel(base.get("finishLevel") == null ? null : String.valueOf(base.get("finishLevel")))
                        .crewCount(asInt(base.get("crewCount")))
                        .build())
                .build();
    }

    // ------------------------------------------------------------ parameters

    private ScheduleParameters toParameters(ScheduleTemplate template, SchedulePreviewRequest request,
                                            Project project) {
        ScheduleParameters params = baseParameters(template);
        params.setStartDate(request.getStartDate() == null ? LocalDate.now() : request.getStartDate());
        if (request.getAreaSqft() != null) params.setAreaSqft(request.getAreaSqft());
        if (request.getRoomCount() != null) params.setRoomCount(request.getRoomCount());
        if (request.getFloorCount() != null) params.setFloorCount(request.getFloorCount());
        if (request.getFinishLevel() != null) params.setFinishLevel(request.getFinishLevel());
        if (request.getCrewCount() != null) params.setCrewCount(request.getCrewCount());
        params.setOccupiedBuilding(request.getOccupiedBuilding());

        if (request.getScopeToggles() != null) params.getScopeToggles().putAll(request.getScopeToggles());
        if (request.getQuantities() != null) params.getQuantities().putAll(request.getQuantities());

        // An unsized project falls back to the template's reference size, which makes the
        // preview a straight copy of the template rather than a silent failure.
        if (params.getAreaSqft() == null) params.setAreaSqft(params.getBaseAreaSqft());
        return params;
    }

    private ScheduleParameters baseParameters(ScheduleTemplate template) {
        Map<String, Object> base = readJsonMap(template.getBaseParametersJson());
        ScheduleParameters params = new ScheduleParameters();
        params.setBaseAreaSqft(asDouble(base.get("areaSqft")));
        params.setBaseRoomCount(asInt(base.get("roomCount")));
        params.setBaseFloorCount(asInt(base.get("floorCount")));
        params.setBaseFinishLevel(base.get("finishLevel") == null ? null : String.valueOf(base.get("finishLevel")));
        params.setBaseCrewCount(asInt(base.get("crewCount")));

        params.setAreaSqft(params.getBaseAreaSqft());
        params.setRoomCount(params.getBaseRoomCount());
        params.setFloorCount(params.getBaseFloorCount());
        params.setFinishLevel(params.getBaseFinishLevel());
        params.setCrewCount(params.getBaseCrewCount());
        return params;
    }

    private List<String> fastTrackConditions(ScheduleTemplate template) {
        if (!template.isFastTrack() || template.getFastTrackConditionsJson() == null) return List.of();
        try {
            return objectMapper.readValue(template.getFastTrackConditionsJson(), JSON_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Fast-track conditions the client has not signed off. Publish is refused while any remain. */
    private List<String> unacknowledged(ScheduleTemplate template, SchedulePreviewRequest request) {
        List<String> conditions = fastTrackConditions(template);
        if (conditions.isEmpty()) return List.of();
        Map<String, Boolean> acks = request.getFastTrackAcknowledgements() == null
                ? Map.of() : request.getFastTrackAcknowledgements();
        return conditions.stream().filter(c -> !Boolean.TRUE.equals(acks.get(c))).toList();
    }

    // ----------------------------------------------------------------- utils

    private ScheduleTemplate resolveTemplate(SchedulePreviewRequest request) {
        if (request.getTemplateUuid() != null) {
            return requireTemplate(request.getTemplateUuid());
        }
        if (request.getTemplateCode() != null) {
            List<ScheduleTemplate> found =
                    templateRepository.findVisibleByCode(request.getTemplateCode(), CompanyContext.get());
            if (!found.isEmpty()) return found.get(0);
        }
        throw new BadRequestException("templateUuid or templateCode is required");
    }

    private ScheduleTemplate requireTemplate(UUID uuid) {
        ScheduleTemplate template = templateRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        if (template.getCompanyId() != null && !template.getCompanyId().equals(CompanyContext.get())) {
            throw new ForbiddenException("Template not in your company");
        }
        return template;
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID ctx = CompanyContext.get();
        if (ctx == null || project.getCompanyId() == null || !ctx.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, JSON_MAP);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer asInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
