package com.fitouts.schedule.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.billing.application.BillingService;
import com.fitouts.planning.application.PlanningService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.roomcollab.domain.ProjectRoom;
import com.fitouts.roomcollab.domain.ProjectRoomRepository;
import com.fitouts.roomcollab.domain.RoomTask;
import com.fitouts.roomcollab.domain.RoomTaskRepository;
import com.fitouts.schedule.api.ProgressUpdateRequest;
import com.fitouts.schedule.api.ProgressUpdateResponse;
import com.fitouts.schedule.api.ProjectScheduleResponse;
import com.fitouts.schedule.api.ScheduleActivityRequest;
import com.fitouts.schedule.api.ScheduleActivityResponse;
import com.fitouts.schedule.api.ScheduleBaselineDetailResponse;
import com.fitouts.schedule.api.ScheduleBaselineRequest;
import com.fitouts.schedule.api.ScheduleBaselineResponse;
import com.fitouts.schedule.api.ScheduleCalendarEventResponse;
import com.fitouts.schedule.api.ScheduleDependencyRequest;
import com.fitouts.schedule.api.ScheduleDependencyResponse;
import com.fitouts.schedule.api.ScheduleFromRoomTaskRequest;
import com.fitouts.schedule.domain.ActivityProgressUpdate;
import com.fitouts.schedule.domain.ActivityProgressUpdateRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.domain.ScheduleBaseline;
import com.fitouts.schedule.domain.ScheduleBaselineActivity;
import com.fitouts.schedule.domain.ScheduleBaselineActivityRepository;
import com.fitouts.schedule.domain.ScheduleBaselineRepository;
import com.fitouts.schedule.domain.ScheduleDependency;
import com.fitouts.schedule.domain.ScheduleDependencyRepository;
import com.fitouts.schedule.domain.SchedulePublishStatus;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.validation.application.ProgressValidationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleActivityRepository activityRepository;
    private final ScheduleDependencyRepository dependencyRepository;
    private final ScheduleBaselineRepository baselineRepository;
    private final ScheduleBaselineActivityRepository baselineActivityRepository;
    private final ActivityProgressUpdateRepository progressRepository;
    private final ProjectService projectService;
    private final PlanningService planningService;
    private final ProgressValidationService progressValidationService;
    private final BillingService billingService;
    private final ProjectRoomRepository projectRoomRepository;
    private final RoomTaskRepository roomTaskRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public ProjectScheduleResponse getSchedule(Long projectId) {
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        List<ScheduleActivity> activities = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        List<ScheduleDependency> dependencies = dependencyRepository.findByProjectIdAndCompanyId(projectId, companyId);
        ActivityEnrichment enrichment = buildEnrichment(activities);
        return ProjectScheduleResponse.builder()
                .projectId(project.getId())
                .ganttPublishAllowed(planningService.get(projectId).isGanttPublishAllowed())
                .activities(activities.stream().map(a -> toActivity(a, enrichment)).toList())
                .dependencies(dependencies.stream().map(this::toDependency).toList())
                .baselines(baselineRepository
                        .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId)
                        .stream().map(this::toBaseline).toList())
                .criticalPath(computeCriticalPath(activities, dependencies))
                .build();
    }

    /**
     * Longest path through FS dependencies by activity duration (calendar days).
     */
    @Transactional(readOnly = true)
    public List<UUID> computeCriticalPath(Long projectId) {
        requireProject(projectId);
        UUID companyId = CompanyContext.get();
        List<ScheduleActivity> activities = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        List<ScheduleDependency> dependencies = dependencyRepository.findByProjectIdAndCompanyId(projectId, companyId);
        return computeCriticalPath(activities, dependencies);
    }

    private List<UUID> computeCriticalPath(List<ScheduleActivity> activities, List<ScheduleDependency> dependencies) {
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }

        Map<UUID, ScheduleActivity> byId = new LinkedHashMap<>();
        Map<UUID, Long> duration = new HashMap<>();
        for (ScheduleActivity a : activities) {
            byId.put(a.getUuid(), a);
            long days = ChronoUnit.DAYS.between(a.getStartDate(), a.getEndDate()) + 1;
            duration.put(a.getUuid(), Math.max(days, 1L));
        }

        Map<UUID, List<UUID>> successors = new HashMap<>();
        Map<UUID, Integer> indegree = new HashMap<>();
        for (UUID id : byId.keySet()) {
            successors.put(id, new ArrayList<>());
            indegree.put(id, 0);
        }
        for (ScheduleDependency dep : dependencies) {
            if (!"FS".equalsIgnoreCase(dep.getDependencyType())) {
                continue;
            }
            UUID pred = dep.getPredecessorUuid();
            UUID succ = dep.getSuccessorUuid();
            if (!byId.containsKey(pred) || !byId.containsKey(succ)) {
                continue;
            }
            successors.get(pred).add(succ);
            indegree.merge(succ, 1, Integer::sum);
        }

        // Longest path DP: dist[v] = max path length ending at v; prev[v] = predecessor on that path
        Map<UUID, Long> dist = new HashMap<>();
        Map<UUID, UUID> prev = new HashMap<>();
        for (UUID id : byId.keySet()) {
            dist.put(id, duration.get(id));
            prev.put(id, null);
        }

        List<UUID> order = topologicalOrder(byId.keySet(), successors, indegree);
        for (UUID u : order) {
            long base = dist.getOrDefault(u, duration.get(u));
            for (UUID v : successors.getOrDefault(u, List.of())) {
                long candidate = base + duration.get(v);
                if (candidate > dist.getOrDefault(v, 0L)) {
                    dist.put(v, candidate);
                    prev.put(v, u);
                }
            }
        }

        UUID end = null;
        long best = -1L;
        for (Map.Entry<UUID, Long> e : dist.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                end = e.getKey();
            }
        }
        if (end == null) {
            return List.of();
        }

        List<UUID> path = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        UUID cur = end;
        while (cur != null && seen.add(cur)) {
            path.add(cur);
            cur = prev.get(cur);
        }
        Collections.reverse(path);
        return path;
    }

    private static List<UUID> topologicalOrder(
            Set<UUID> nodes, Map<UUID, List<UUID>> successors, Map<UUID, Integer> indegreeIn) {
        Map<UUID, Integer> indegree = new HashMap<>(indegreeIn);
        List<UUID> queue = new ArrayList<>();
        for (UUID id : nodes) {
            if (indegree.getOrDefault(id, 0) == 0) {
                queue.add(id);
            }
        }
        List<UUID> order = new ArrayList<>();
        int i = 0;
        while (i < queue.size()) {
            UUID u = queue.get(i++);
            order.add(u);
            for (UUID v : successors.getOrDefault(u, List.of())) {
                int next = indegree.merge(v, -1, Integer::sum);
                if (next == 0) {
                    queue.add(v);
                }
            }
        }
        // Cycle fallback: append remaining nodes
        if (order.size() < nodes.size()) {
            for (UUID id : nodes) {
                if (!order.contains(id)) {
                    order.add(id);
                }
            }
        }
        return order;
    }

    @Transactional
    public ScheduleActivityResponse createActivity(Long projectId, ScheduleActivityRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        validateActivityRequest(request);

        ScheduleActivity activity = new ScheduleActivity();
        activity.setProjectId(project.getId());
        activity.setCompanyId(CompanyContext.get());
        applyRequest(activity, request);
        validateAndApplyRoomLinks(activity, request, project.getId());
        activity.setPublishStatus(SchedulePublishStatus.DRAFT);
        activity.setCreatedBy(principal.getAccountId());
        ScheduleActivity saved = activityRepository.save(activity);
        return toActivity(saved, buildEnrichment(List.of(saved)));
    }

    @Transactional
    public ScheduleActivityResponse createActivityFromRoomTask(Long projectId, ScheduleFromRoomTaskRequest request) {
        if (request == null || request.getRoomTaskId() == null) {
            throw new BadRequestException("roomTaskId is required");
        }
        RoomTask task = roomTaskRepository.findByUuidAndProjectId(request.getRoomTaskId(), projectId)
                .orElseThrow(() -> new BadRequestException("Room task not found in this project"));
        assertCompanyId(task.getCompanyId());

        LocalDate start = LocalDate.now();
        LocalDate end = task.getClientDeadline() != null
                ? task.getClientDeadline().toLocalDate()
                : start.plusDays(7);
        if (end.isBefore(start)) {
            end = start;
        }

        ScheduleActivityRequest activityRequest = new ScheduleActivityRequest();
        activityRequest.setName(task.getTitle());
        activityRequest.setStartDate(start);
        activityRequest.setEndDate(end);
        activityRequest.setRoomTaskId(task.getUuid());
        activityRequest.setProjectRoomId(task.getProjectRoomId());
        if (task.getAssigneeAccountId() != null) {
            activityRequest.setAssigneeAccountId(task.getAssigneeAccountId());
        }
        return createActivity(projectId, activityRequest);
    }

    @Transactional
    public ScheduleActivityResponse updateActivity(UUID activityUuid, ScheduleActivityRequest request) {
        requireStaff();
        ScheduleActivity activity = requireActivity(activityUuid);
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        if (request.getStartDate() != null && request.getEndDate() == null
                && activity.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        if (request.getEndDate() != null && request.getStartDate() == null
                && request.getEndDate().isBefore(activity.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        applyRequest(activity, request);
        validateAndApplyRoomLinks(activity, request, activity.getProjectId());
        if (!StringUtils.hasText(activity.getName())) {
            throw new BadRequestException("name is required");
        }
        ScheduleActivity saved = activityRepository.save(activity);
        return toActivity(saved, buildEnrichment(List.of(saved)));
    }

    @Transactional
    public void deleteActivity(UUID activityUuid) {
        requireStaff();
        ScheduleActivity activity = requireActivity(activityUuid);
        dependencyRepository.deleteByPredecessorUuidOrSuccessorUuid(activityUuid, activityUuid);
        activityRepository.delete(activity);
    }

    @Transactional
    public ScheduleDependencyResponse addDependency(Long projectId, ScheduleDependencyRequest request) {
        requireStaff();
        Project project = requireProject(projectId);
        if (request.getPredecessorUuid() == null || request.getSuccessorUuid() == null) {
            throw new BadRequestException("predecessorUuid and successorUuid are required");
        }
        if (request.getPredecessorUuid().equals(request.getSuccessorUuid())) {
            throw new BadRequestException("Activity cannot depend on itself");
        }
        ScheduleActivity pred = requireActivityInProject(request.getPredecessorUuid(), project.getId());
        ScheduleActivity succ = requireActivityInProject(request.getSuccessorUuid(), project.getId());

        ScheduleDependency dep = new ScheduleDependency();
        dep.setProjectId(project.getId());
        dep.setCompanyId(CompanyContext.get());
        dep.setPredecessorUuid(pred.getUuid());
        dep.setSuccessorUuid(succ.getUuid());
        dep.setDependencyType("FS");
        return toDependency(dependencyRepository.save(dep));
    }

    @Transactional
    public void deleteDependency(UUID dependencyUuid) {
        requireStaff();
        ScheduleDependency dep = dependencyRepository.findById(dependencyUuid)
                .orElseThrow(() -> new NotFoundException("Dependency not found"));
        assertCompanyId(dep.getCompanyId());
        dependencyRepository.delete(dep);
    }

    @Transactional
    public ProjectScheduleResponse publish(Long projectId) {
        requireStaff();
        requireProject(projectId);
        planningService.assertCanPublishGantt(projectId);
        UUID companyId = CompanyContext.get();
        List<ScheduleActivity> activities = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        if (activities.isEmpty()) {
            throw new BadRequestException("Add at least one activity before publishing");
        }
        for (ScheduleActivity a : activities) {
            a.setPublishStatus(SchedulePublishStatus.PUBLISHED);
        }
        activityRepository.saveAll(activities);
        return getSchedule(projectId);
    }

    @Transactional
    public ScheduleBaselineResponse createBaseline(Long projectId, ScheduleBaselineRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        String name = StringUtils.hasText(request.getName())
                ? request.getName().trim()
                : "Baseline " + (baselineRepository
                        .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId).size() + 1);

        ScheduleBaseline baseline = new ScheduleBaseline();
        baseline.setProjectId(project.getId());
        baseline.setCompanyId(companyId);
        baseline.setName(name);
        baseline.setCreatedBy(principal.getAccountId());
        baseline = baselineRepository.save(baseline);

        List<ScheduleActivity> activities = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        for (ScheduleActivity a : activities) {
            ScheduleBaselineActivity snap = new ScheduleBaselineActivity();
            snap.setBaselineUuid(baseline.getUuid());
            snap.setActivityUuid(a.getUuid());
            snap.setName(a.getName());
            snap.setStartDate(a.getStartDate());
            snap.setEndDate(a.getEndDate());
            snap.setPercentComplete(a.getPercentComplete());
            snap.setWeight(a.getWeight() != null ? a.getWeight() : BigDecimal.ONE);
            baselineActivityRepository.save(snap);
        }
        return toBaseline(baseline);
    }

    @Transactional(readOnly = true)
    public ScheduleBaselineDetailResponse getBaseline(Long projectId, UUID baselineUuid) {
        requireProject(projectId);
        ScheduleBaseline baseline = baselineRepository.findById(baselineUuid)
                .orElseThrow(() -> new NotFoundException("Baseline not found"));
        assertCompanyId(baseline.getCompanyId());
        if (!baseline.getProjectId().equals(projectId)) {
            throw new BadRequestException("Baseline does not belong to this project");
        }
        List<ScheduleBaselineDetailResponse.BaselineActivitySnap> snaps = baselineActivityRepository
                .findByBaselineUuid(baselineUuid)
                .stream()
                .map(a -> ScheduleBaselineDetailResponse.BaselineActivitySnap.builder()
                        .activityUuid(a.getActivityUuid())
                        .name(a.getName())
                        .startDate(a.getStartDate())
                        .endDate(a.getEndDate())
                        .percentComplete(a.getPercentComplete())
                        .weight(a.getWeight())
                        .build())
                .toList();
        return ScheduleBaselineDetailResponse.builder()
                .uuid(baseline.getUuid())
                .name(baseline.getName())
                .createdAt(baseline.getCreatedAt())
                .createdBy(baseline.getCreatedBy())
                .activities(snaps)
                .build();
    }

    @Transactional
    public ProgressUpdateResponse postProgress(UUID activityUuid, ProgressUpdateRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        ScheduleActivity activity = requireActivity(activityUuid);
        assertCanReportProgress(principal, activity);

        if (request.getPercentComplete() == null) {
            throw new BadRequestException("percentComplete is required");
        }
        int pct = request.getPercentComplete();
        if (pct < 0 || pct > 100) {
            throw new BadRequestException("percentComplete must be 0–100");
        }

        ActivityProgressUpdate update = new ActivityProgressUpdate();
        update.setActivityUuid(activity.getUuid());
        update.setProjectId(activity.getProjectId());
        update.setCompanyId(activity.getCompanyId());
        update.setPercentComplete(pct);
        update.setNotes(request.getNotes());
        update.setLabourHours(request.getLabourHours());
        update.setReportedBy(principal.getAccountId());
        update = progressRepository.save(update);

        activity.setPercentComplete(pct);
        activityRepository.save(activity);

        // Wave B: auto-create PENDING PM validation for the progress claim
        progressValidationService.createPendingForProgress(update);

        // UAT closeout: auto-trigger billing milestones linked to this activity
        billingService.evaluateTriggersForActivity(activity.getUuid());

        return toProgress(update);
    }

    @Transactional(readOnly = true)
    public List<ProgressUpdateResponse> listProgress(UUID activityUuid) {
        requireActivity(activityUuid);
        return progressRepository.findByActivityUuidOrderByReportedAtDesc(activityUuid)
                .stream().map(this::toProgress).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleCalendarEventResponse> calendarEvents(
            LocalDate startDate, LocalDate endDate, Long projectId, Long assigneeAccountId) {
        requireStaff();
        if (startDate == null || endDate == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        UUID companyId = CompanyContext.get();
        List<ScheduleActivity> activities = activityRepository.findPublishedInDateRange(
                companyId, startDate, endDate, projectId, assigneeAccountId);
        ActivityEnrichment enrichment = buildEnrichment(activities);
        Map<Long, String> projectNames = loadProjectNames(activities);
        return activities.stream()
                .map(a -> toCalendarEvent(a, enrichment, projectNames.get(a.getProjectId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleActivityResponse> myActivities() {
        AuthPrincipal principal = requireAuthenticated();
        UUID companyId = CompanyContext.get();
        List<ScheduleActivity> activities = activityRepository
                .findByAssigneeAccountIdAndCompanyIdOrderByStartDateAsc(principal.getAccountId(), companyId)
                .stream()
                .filter(a -> a.getPublishStatus() == SchedulePublishStatus.PUBLISHED)
                .toList();
        ActivityEnrichment enrichment = buildEnrichment(activities);
        return activities.stream().map(a -> toActivity(a, enrichment)).toList();
    }

    private void validateAndApplyRoomLinks(ScheduleActivity activity, ScheduleActivityRequest request, Long projectId) {
        if (Boolean.TRUE.equals(request.getClearRoomLinks())) {
            activity.setProjectRoomId(null);
            activity.setRoomTaskId(null);
            return;
        }
        if (request.getRoomTaskId() != null) {
            RoomTask task = roomTaskRepository.findByUuidAndProjectId(request.getRoomTaskId(), projectId)
                    .orElseThrow(() -> new BadRequestException("Room task not found in this project"));
            assertCompanyId(task.getCompanyId());
            activity.setRoomTaskId(task.getUuid());
            activity.setProjectRoomId(task.getProjectRoomId());
            return;
        }
        if (request.getProjectRoomId() != null) {
            ProjectRoom room = projectRoomRepository.findByUuidAndProjectId(request.getProjectRoomId(), projectId)
                    .orElseThrow(() -> new BadRequestException("Project room not found in this project"));
            assertCompanyId(room.getCompanyId());
            activity.setProjectRoomId(room.getUuid());
            activity.setRoomTaskId(null);
        }
    }

    private ActivityEnrichment buildEnrichment(List<ScheduleActivity> activities) {
        Map<UUID, ProjectRoom> roomsById = new HashMap<>();
        Map<UUID, RoomTask> tasksById = new HashMap<>();
        Map<Long, Account> accountsById = new HashMap<>();

        for (ScheduleActivity a : activities) {
            if (a.getProjectRoomId() != null) {
                roomsById.putIfAbsent(a.getProjectRoomId(), null);
            }
            if (a.getRoomTaskId() != null) {
                tasksById.putIfAbsent(a.getRoomTaskId(), null);
            }
            if (a.getAssigneeAccountId() != null) {
                accountsById.putIfAbsent(a.getAssigneeAccountId(), null);
            }
        }

        for (UUID roomId : roomsById.keySet()) {
            projectRoomRepository.findById(roomId).ifPresent(r -> roomsById.put(roomId, r));
        }
        for (UUID taskId : tasksById.keySet()) {
            roomTaskRepository.findById(taskId).ifPresent(t -> tasksById.put(taskId, t));
        }
        for (Long accountId : accountsById.keySet()) {
            accountRepository.findById(accountId).ifPresent(acc -> accountsById.put(accountId, acc));
        }

        return new ActivityEnrichment(roomsById, tasksById, accountsById);
    }

    private Map<Long, String> loadProjectNames(List<ScheduleActivity> activities) {
        Map<Long, String> names = new HashMap<>();
        for (ScheduleActivity a : activities) {
            if (names.containsKey(a.getProjectId())) {
                continue;
            }
            try {
                Project p = projectService.getById(a.getProjectId());
                names.put(a.getProjectId(), p.getName());
            } catch (Exception e) {
                names.put(a.getProjectId(), "Project " + a.getProjectId());
            }
        }
        return names;
    }

    private record ActivityEnrichment(
            Map<UUID, ProjectRoom> roomsById,
            Map<UUID, RoomTask> tasksById,
            Map<Long, Account> accountsById) {
    }

    private void applyRequest(ScheduleActivity activity, ScheduleActivityRequest request) {
        if (StringUtils.hasText(request.getName())) activity.setName(request.getName().trim());
        if (request.getStartDate() != null) activity.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) activity.setEndDate(request.getEndDate());
        if (request.getPercentComplete() != null) {
            int pct = request.getPercentComplete();
            if (pct < 0 || pct > 100) throw new BadRequestException("percentComplete must be 0–100");
            activity.setPercentComplete(pct);
        }
        if (request.getWeight() != null) activity.setWeight(request.getWeight());
        if (request.getParentUuid() != null) activity.setParentUuid(request.getParentUuid());
        if (request.getAssigneeAccountId() != null) activity.setAssigneeAccountId(request.getAssigneeAccountId());
        if (request.getSortOrder() != null) activity.setSortOrder(request.getSortOrder());
        if (request.getDelayReason() != null) {
            activity.setDelayReason(StringUtils.hasText(request.getDelayReason())
                    ? request.getDelayReason().trim() : null);
        }
    }

    private void validateActivityRequest(ScheduleActivityRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new BadRequestException("name is required");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        assertCompanyId(project.getCompanyId());
        return project;
    }

    private ScheduleActivity requireActivity(UUID uuid) {
        ScheduleActivity activity = activityRepository.findByUuidAndCompanyId(uuid, CompanyContext.get())
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        return activity;
    }

    private ScheduleActivity requireActivityInProject(UUID uuid, Long projectId) {
        ScheduleActivity activity = requireActivity(uuid);
        if (!activity.getProjectId().equals(projectId)) {
            throw new BadRequestException("Activity does not belong to this project");
        }
        return activity;
    }

    private void assertCompanyId(UUID companyId) {
        UUID ctx = CompanyContext.get();
        if (ctx == null || companyId == null || !ctx.equals(companyId)) {
            throw new ForbiddenException("Project not in your company");
        }
    }

    private void assertCanReportProgress(AuthPrincipal principal, ScheduleActivity activity) {
        if (isPmOrAdmin(principal)) return;
        if (activity.getAssigneeAccountId() != null
                && activity.getAssigneeAccountId().equals(principal.getAccountId())) {
            return;
        }
        throw new ForbiddenException("Only the assignee or PM/Admin can post progress");
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private boolean isPmOrAdmin(AuthPrincipal principal) {
        if (principal.getRoles() == null) return false;
        return principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN)
                || principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.PROJECT_MANAGER);
    }

    private ScheduleActivityResponse toActivity(ScheduleActivity a, ActivityEnrichment enrichment) {
        ProjectRoom room = a.getProjectRoomId() != null
                ? enrichment.roomsById().get(a.getProjectRoomId()) : null;
        RoomTask task = a.getRoomTaskId() != null
                ? enrichment.tasksById().get(a.getRoomTaskId()) : null;
        Account assignee = a.getAssigneeAccountId() != null
                ? enrichment.accountsById().get(a.getAssigneeAccountId()) : null;

        String roomName = room != null ? room.getName() : null;
        if (roomName == null && task != null && task.getProjectRoomId() != null) {
            ProjectRoom taskRoom = enrichment.roomsById().get(task.getProjectRoomId());
            if (taskRoom == null) {
                taskRoom = projectRoomRepository.findById(task.getProjectRoomId()).orElse(null);
            }
            roomName = taskRoom != null ? taskRoom.getName() : null;
        }

        return ScheduleActivityResponse.builder()
                .uuid(a.getUuid())
                .projectId(a.getProjectId())
                .name(a.getName())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .percentComplete(a.getPercentComplete())
                .weight(a.getWeight())
                .parentUuid(a.getParentUuid())
                .projectRoomId(a.getProjectRoomId())
                .roomTaskId(a.getRoomTaskId())
                .assigneeAccountId(a.getAssigneeAccountId())
                .publishStatus(a.getPublishStatus())
                .sortOrder(a.getSortOrder())
                .delayReason(a.getDelayReason())
                .updatedAt(a.getUpdatedAt())
                .roomName(roomName)
                .roomTaskTitle(task != null ? task.getTitle() : null)
                .roomTaskStatus(task != null && task.getStatus() != null ? task.getStatus().name() : null)
                .assigneeName(assignee != null ? assignee.getFullName() : null)
                .build();
    }

    private ScheduleCalendarEventResponse toCalendarEvent(
            ScheduleActivity a, ActivityEnrichment enrichment, String projectName) {
        ScheduleActivityResponse base = toActivity(a, enrichment);
        return ScheduleCalendarEventResponse.builder()
                .uuid(base.getUuid())
                .projectId(base.getProjectId())
                .projectName(projectName)
                .name(base.getName())
                .startDate(base.getStartDate())
                .endDate(base.getEndDate())
                .percentComplete(base.getPercentComplete())
                .assigneeAccountId(base.getAssigneeAccountId())
                .assigneeName(base.getAssigneeName())
                .projectRoomId(base.getProjectRoomId())
                .roomName(base.getRoomName())
                .roomTaskId(base.getRoomTaskId())
                .roomTaskTitle(base.getRoomTaskTitle())
                .build();
    }

    private ScheduleActivityResponse toActivity(ScheduleActivity a) {
        return toActivity(a, buildEnrichment(List.of(a)));
    }

    private ScheduleDependencyResponse toDependency(ScheduleDependency d) {
        return ScheduleDependencyResponse.builder()
                .uuid(d.getUuid())
                .predecessorUuid(d.getPredecessorUuid())
                .successorUuid(d.getSuccessorUuid())
                .dependencyType(d.getDependencyType())
                .build();
    }

    private ScheduleBaselineResponse toBaseline(ScheduleBaseline b) {
        return ScheduleBaselineResponse.builder()
                .uuid(b.getUuid())
                .name(b.getName())
                .createdAt(b.getCreatedAt())
                .createdBy(b.getCreatedBy())
                .build();
    }

    private ProgressUpdateResponse toProgress(ActivityProgressUpdate u) {
        return ProgressUpdateResponse.builder()
                .uuid(u.getUuid())
                .activityUuid(u.getActivityUuid())
                .percentComplete(u.getPercentComplete())
                .notes(u.getNotes())
                .labourHours(u.getLabourHours())
                .reportedBy(u.getReportedBy())
                .reportedAt(u.getReportedAt())
                .build();
    }
}
