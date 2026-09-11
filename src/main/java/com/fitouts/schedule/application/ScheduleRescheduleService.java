package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.approval.domain.ApprovalCase;
import com.fitouts.approval.domain.ApprovalCaseRepository;
import com.fitouts.schedule.api.LiveCpmSnapshot;
import com.fitouts.schedule.api.RescheduleRequest;
import com.fitouts.schedule.api.RescheduleResponse;
import com.fitouts.schedule.domain.ProjectSchedule;
import com.fitouts.schedule.domain.ProjectScheduleRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.domain.ScheduleDependency;
import com.fitouts.schedule.domain.ScheduleDependencyRepository;
import com.fitouts.schedule.engine.CpmActivity;
import com.fitouts.schedule.engine.CpmEngine;
import com.fitouts.schedule.engine.CpmLink;
import com.fitouts.schedule.engine.CpmResult;
import com.fitouts.schedule.engine.DependencyType;
import com.fitouts.schedule.engine.WorkingCalendar;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import lombok.RequiredArgsConstructor;

/**
 * Re-runs CPM over the live programme after a change, and pushes the resulting dates back out
 * to packages and approval cases.
 *
 * <p>This is what a Gantt bar drag calls. The client does not compute dates: it asks for a
 * duration or constraint change and gets the whole rescheduled network back, because a local
 * date edit would silently desynchronise the successors.
 */
@Service
@RequiredArgsConstructor
public class ScheduleRescheduleService {

    private final ScheduleActivityRepository activityRepository;
    private final ScheduleDependencyRepository dependencyRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final ApprovalCaseRepository approvalCaseRepository;
    private final WorkCalendarService workCalendarService;
    private final CpmEngine cpmEngine;

    @Transactional
    public RescheduleResponse reschedule(Long projectId, RescheduleRequest request) {
        UUID companyId = CompanyContext.get();
        List<ScheduleActivity> activities = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        if (activities.isEmpty()) {
            throw new BadRequestException("This project has no schedule to reschedule");
        }
        List<ScheduleDependency> dependencies =
                dependencyRepository.findByProjectIdAndCompanyId(projectId, companyId);

        ProjectSchedule schedule = projectScheduleRepository.findByProjectId(projectId).orElse(null);
        WorkingCalendar calendar = workCalendarService.resolve(
                schedule == null ? null : schedule.getWorkCalendarUuid());

        Map<UUID, ScheduleActivity> byUuid = new LinkedHashMap<>();
        for (ScheduleActivity a : activities) byUuid.put(a.getUuid(), a);

        List<String> refusals = new ArrayList<>();
        if (request != null && request.getActivityUuid() != null) {
            applyEdit(request, byUuid, dependencies, calendar, refusals);
        }

        LocalDate projectStart = request != null && request.getProjectStartDate() != null
                ? request.getProjectStartDate()
                : activities.stream().map(ScheduleActivity::getStartDate)
                        .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(LocalDate.now());

        CpmResult result = solve(activities, dependencies, projectStart, calendar);
        writeBack(activities, result);
        activityRepository.saveAll(activities);

        int packagesUpdated = syncPackageDates(projectId, companyId, activities);
        int casesUpdated = syncApprovalTargets(projectId, companyId, activities, calendar);

        LocalDate previousFinish = schedule == null ? null : schedule.getCurrentFinishDate();
        if (schedule != null) {
            schedule.setCurrentFinishDate(result.getProjectFinish());
            schedule.setComputedWorkingDays(result.getTotalWorkingDays());
            projectScheduleRepository.save(schedule);
        }

        Long slip = previousFinish == null || result.getProjectFinish() == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(previousFinish, result.getProjectFinish());

        return RescheduleResponse.builder()
                .projectStart(result.getProjectStart())
                .projectFinish(result.getProjectFinish())
                .workingDays(result.getTotalWorkingDays())
                .previousFinish(previousFinish)
                .finishMovedByDays(slip)
                .criticalActivityCodes(result.criticalActivities().stream()
                        .map(CpmActivity::getCode).filter(java.util.Objects::nonNull).toList())
                .criticalPaths(result.getCriticalPaths())
                .packagesUpdated(packagesUpdated)
                .approvalTargetsUpdated(casesUpdated)
                .refusals(refusals)
                .warnings(result.getWarnings())
                .build();
    }

    /** Re-solves the live network with no bar edit (after dependency / duration changes). */
    @Transactional
    public RescheduleResponse refreshNetwork(Long projectId) {
        return reschedule(projectId, new RescheduleRequest());
    }

    /**
     * Read-only CPM analysis for GET schedule — same engine as preview, no persistence.
     */
    @Transactional(readOnly = true)
    public LiveCpmSnapshot analyze(Long projectId, List<ScheduleActivity> activities,
                                   List<ScheduleDependency> dependencies) {
        if (activities == null || activities.isEmpty()) {
            return LiveCpmSnapshot.builder().build();
        }
        ProjectSchedule schedule = projectScheduleRepository.findByProjectId(projectId).orElse(null);
        WorkingCalendar calendar = workCalendarService.resolve(
                schedule == null ? null : schedule.getWorkCalendarUuid());
        LocalDate projectStart = activities.stream()
                .map(ScheduleActivity::getStartDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        CpmResult result = solve(activities, dependencies, projectStart, calendar);

        Map<String, UUID> uuidByCode = new HashMap<>();
        for (ScheduleActivity a : activities) {
            String code = a.getActivityCode() != null ? a.getActivityCode() : a.getUuid().toString();
            uuidByCode.put(code, a.getUuid());
        }

        Map<UUID, Boolean> criticalByUuid = new HashMap<>();
        Map<UUID, Integer> totalFloatByUuid = new HashMap<>();
        Map<UUID, Integer> freeFloatByUuid = new HashMap<>();
        for (CpmActivity node : result.getActivities()) {
            UUID id = uuidByCode.get(node.getCode());
            if (id == null) continue;
            criticalByUuid.put(id, node.isCritical());
            totalFloatByUuid.put(id, node.getTotalFloat());
            freeFloatByUuid.put(id, node.getFreeFloat());
        }

        List<List<UUID>> pathUuids = new ArrayList<>();
        for (List<String> path : result.getCriticalPaths()) {
            List<UUID> uuids = new ArrayList<>();
            for (String code : path) {
                UUID id = uuidByCode.get(code);
                if (id != null) uuids.add(id);
            }
            if (!uuids.isEmpty()) pathUuids.add(uuids);
        }

        List<UUID> primary = pathUuids.isEmpty() ? List.of() : pathUuids.get(0);

        return LiveCpmSnapshot.builder()
                .criticalPath(primary)
                .criticalPaths(pathUuids)
                .criticalByUuid(criticalByUuid)
                .totalFloatByUuid(totalFloatByUuid)
                .freeFloatByUuid(freeFloatByUuid)
                .build();
    }

    /**
     * Applies the requested edit. A drag that would shorten a locked lag is refused outright:
     * the lag represents curing or a statutory hold, and honouring the drag would produce a
     * programme that cannot be built.
     */
    private void applyEdit(RescheduleRequest request, Map<UUID, ScheduleActivity> byUuid,
                           List<ScheduleDependency> dependencies, WorkingCalendar calendar,
                           List<String> refusals) {
        ScheduleActivity target = byUuid.get(request.getActivityUuid());
        if (target == null) throw new NotFoundException("Activity not found in this project");

        if (request.getDurationWorkingDays() != null) {
            if (target.isLockedDuration()) {
                refusals.add(target.getName() + " has a locked duration"
                        + (target.getConstraintNote() == null ? "" : " (" + target.getConstraintNote() + ")")
                        + ", so it was left unchanged.");
            } else if (request.getDurationWorkingDays() < 1) {
                throw new BadRequestException("Duration must be at least one working day");
            } else {
                target.setDurationWorkingDays(request.getDurationWorkingDays());
            }
        }

        if (request.getNewStartDate() != null) {
            ScheduleDependency lockedDriver = dependencies.stream()
                    .filter(d -> d.getSuccessorUuid().equals(target.getUuid()) && d.isLocked())
                    .findFirst().orElse(null);

            if (lockedDriver != null && request.getNewStartDate().isBefore(target.getStartDate())) {
                ScheduleActivity pred = byUuid.get(lockedDriver.getPredecessorUuid());
                refusals.add(String.format(
                        "%s cannot start earlier: it follows %s by a locked %d working-day hold%s.",
                        target.getName(),
                        pred == null ? "its predecessor" : pred.getName(),
                        lockedDriver.getLagWorkingDays(),
                        lockedDriver.getLockReason() == null ? "" : " (" + lockedDriver.getLockReason() + ")"));
            } else {
                // Expressed as a constraint so the forward pass still honours the logic:
                // an activity may be held later than its predecessors, never earlier.
                target.setConstraintStartDate(calendar.nextWorkingDay(request.getNewStartDate()));
            }
        }

        if (Boolean.TRUE.equals(request.getClearConstraint())) {
            target.setConstraintStartDate(null);
            target.setConstrainedByCaseUuid(null);
        }
    }

    private CpmResult solve(List<ScheduleActivity> activities, List<ScheduleDependency> dependencies,
                            LocalDate projectStart, WorkingCalendar calendar) {
        Map<UUID, String> codeByUuid = new HashMap<>();
        List<CpmActivity> nodes = new ArrayList<>();

        for (ScheduleActivity a : activities) {
            String code = a.getActivityCode() != null ? a.getActivityCode() : a.getUuid().toString();
            codeByUuid.put(a.getUuid(), code);

            CpmActivity node = new CpmActivity();
            node.setCode(code);
            node.setName(a.getName());
            node.setWbsPhase(a.getWbsPhase());
            node.setTradePackageCode(a.getTradePackageCode());
            node.setDurationWorkingDays(durationOf(a, calendar));
            node.setMilestone(a.isMilestone());
            node.setLockedDuration(a.isLockedDuration());
            node.setConstraintNote(a.getConstraintNote());
            node.setConstraintStart(a.getConstraintStartDate());
            node.setRef(a);
            nodes.add(node);
        }

        List<CpmLink> links = new ArrayList<>();
        for (ScheduleDependency d : dependencies) {
            String pred = codeByUuid.get(d.getPredecessorUuid());
            String succ = codeByUuid.get(d.getSuccessorUuid());
            if (pred == null || succ == null) continue;
            CpmLink link = new CpmLink();
            link.setPredecessorCode(pred);
            link.setSuccessorCode(succ);
            link.setType(DependencyType.from(d.getDependencyType()));
            link.setLagWorkingDays(d.getLagWorkingDays());
            link.setLocked(d.isLocked());
            link.setLockReason(d.getLockReason());
            links.add(link);
        }

        return cpmEngine.solve(nodes, links, projectStart, calendar);
    }

    /** Falls back to the stored dates for activities created before the engine existed. */
    private int durationOf(ScheduleActivity a, WorkingCalendar calendar) {
        if (a.getDurationWorkingDays() != null && a.getDurationWorkingDays() > 0) {
            return a.getDurationWorkingDays();
        }
        if (a.getStartDate() != null && a.getEndDate() != null) {
            return Math.max(1, calendar.workingDaysBetweenInclusive(a.getStartDate(), a.getEndDate()));
        }
        return 1;
    }

    private void writeBack(List<ScheduleActivity> activities, CpmResult result) {
        Map<String, CpmActivity> solved = new HashMap<>();
        for (CpmActivity node : result.getActivities()) solved.put(node.getCode(), node);

        for (ScheduleActivity a : activities) {
            String code = a.getActivityCode() != null ? a.getActivityCode() : a.getUuid().toString();
            CpmActivity node = solved.get(code);
            if (node == null || node.getEarlyStart() == null) continue;
            a.setStartDate(node.getEarlyStart());
            a.setEndDate(node.getEarlyFinish());
            a.setEarlyStart(node.getEarlyStart());
            a.setEarlyFinish(node.getEarlyFinish());
            a.setLateStart(node.getLateStart());
            a.setLateFinish(node.getLateFinish());
            a.setTotalFloat(node.getTotalFloat());
            a.setFreeFloat(node.getFreeFloat());
            a.setCritical(node.isCritical());
            a.setDurationWorkingDays(node.effectiveDuration());
        }
    }

    // -------------------------------------------------------------- live sync

    private int syncPackageDates(Long projectId, UUID companyId, List<ScheduleActivity> activities) {
        Map<String, List<ScheduleActivity>> byTrade = new LinkedHashMap<>();
        for (ScheduleActivity a : activities) {
            if (a.getTradePackageCode() == null) continue;
            byTrade.computeIfAbsent(a.getTradePackageCode(), k -> new ArrayList<>()).add(a);
        }
        if (byTrade.isEmpty()) return 0;

        int updated = 0;
        for (SubcontractorPackage pkg : packageRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId)) {
            List<ScheduleActivity> trade = byTrade.get(pkg.getTradePackageCode());
            if (trade == null || trade.isEmpty()) continue;
            pkg.setPlannedStart(trade.stream().map(ScheduleActivity::getStartDate)
                    .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null));
            pkg.setPlannedFinish(trade.stream().map(ScheduleActivity::getEndDate)
                    .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null));
            packageRepository.save(pkg);
            updated++;
        }
        return updated;
    }

    /**
     * Pulls each blocking case's target submission date back in line with the activities it
     * blocks. A programme that moves earlier makes permits more urgent, not less.
     */
    private int syncApprovalTargets(Long projectId, UUID companyId, List<ScheduleActivity> activities,
                                    WorkingCalendar calendar) {
        Map<String, ScheduleActivity> byCode = new HashMap<>();
        for (ScheduleActivity a : activities) {
            if (a.getActivityCode() != null) byCode.put(a.getActivityCode(), a);
        }

        int updated = 0;
        for (ApprovalCase c : approvalCaseRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtAsc(projectId, companyId)) {
            if (c.getBlocksActivityCodes() == null || c.getStatus().isTerminal()) continue;

            LocalDate earliest = null;
            for (String part : c.getBlocksActivityCodes().split("[,;]")) {
                ScheduleActivity a = byCode.get(part.trim());
                if (a == null || a.getStartDate() == null) continue;
                if (earliest == null || a.getStartDate().isBefore(earliest)) earliest = a.getStartDate();
            }
            if (earliest == null) continue;

            int sla = c.getSlaDays() == null ? 0 : c.getSlaDays();
            LocalDate requiredApproval = calendar.previousWorkingDay(earliest.minusDays(1));
            c.setTargetSubmissionDate(calendar.subtractWorkingDays(requiredApproval, Math.max(sla, 0)));
            approvalCaseRepository.save(c);
            updated++;
        }
        return updated;
    }
}
