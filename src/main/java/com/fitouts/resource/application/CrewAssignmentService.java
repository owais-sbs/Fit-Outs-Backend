package com.fitouts.resource.application;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.planning.application.PlanningService;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.resource.api.CrewAssignmentRequest;
import com.fitouts.resource.api.CrewAssignmentResponse;
import com.fitouts.resource.api.ResourceUtilisationResponse;
import com.fitouts.resource.domain.ActivityCrewAssignment;
import com.fitouts.resource.domain.ActivityCrewAssignmentRepository;
import com.fitouts.resource.domain.LabourCrew;
import com.fitouts.resource.domain.LabourCrewRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrewAssignmentService {

    private final ActivityCrewAssignmentRepository assignmentRepository;
    private final LabourCrewRepository labourCrewRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ProjectService projectService;
    private final PlanningService planningService;

    @Transactional(readOnly = true)
    public List<CrewAssignmentResponse> list(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        return assignmentRepository.findByProjectIdAndCompanyIdOrderByStartDateAsc(project.getId(), companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CrewAssignmentResponse create(Long projectId, CrewAssignmentRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        if (request.getActivityUuid() == null || request.getCrewUuid() == null) {
            throw new BadRequestException("activityUuid and crewUuid are required");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }

        ScheduleActivity activity = activityRepository
                .findByUuidAndCompanyId(request.getActivityUuid(), companyId)
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        if (!activity.getProjectId().equals(project.getId())) {
            throw new BadRequestException("Activity does not belong to this project");
        }

        LabourCrew crew = labourCrewRepository.findByUuidAndCompanyId(request.getCrewUuid(), companyId)
                .orElseThrow(() -> new NotFoundException("Labour crew not found"));
        if (!crew.isActive()) {
            throw new BadRequestException("Labour crew is inactive");
        }

        List<ActivityCrewAssignment> overlaps = assignmentRepository.findOverlapping(
                crew.getUuid(), companyId, request.getStartDate(), request.getEndDate());
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("Crew is already assigned on overlapping dates");
        }

        ActivityCrewAssignment assignment = new ActivityCrewAssignment();
        assignment.setActivityUuid(activity.getUuid());
        assignment.setCrewUuid(crew.getUuid());
        assignment.setProjectId(project.getId());
        assignment.setCompanyId(companyId);
        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());
        assignment = assignmentRepository.save(assignment);

        planningService.syncLabourAndResourceStatus(project.getId(), PlanAreaStatus.IN_PROGRESS, principal.getAccountId());
        return toResponse(assignment);
    }

    @Transactional
    public void delete(Long projectId, UUID assignmentUuid) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        ActivityCrewAssignment assignment = assignmentRepository.findByUuidAndCompanyId(assignmentUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Crew assignment not found"));
        if (!assignment.getProjectId().equals(project.getId())) {
            throw new BadRequestException("Assignment does not belong to this project");
        }
        assignmentRepository.delete(assignment);

        long remaining = assignmentRepository.countByProjectIdAndCompanyId(project.getId(), companyId);
        if (remaining == 0) {
            planningService.syncLabourAndResourceStatus(project.getId(), PlanAreaStatus.NOT_STARTED, principal.getAccountId());
        }
    }

    @Transactional(readOnly = true)
    public ResourceUtilisationResponse utilisation(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        List<ActivityCrewAssignment> assignments = assignmentRepository
                .findByProjectIdAndCompanyIdOrderByStartDateAsc(project.getId(), companyId);

        Map<UUID, Long> days = new HashMap<>();
        Map<UUID, Long> counts = new HashMap<>();
        long totalDays = 0;

        for (ActivityCrewAssignment a : assignments) {
            long assignedDays = ChronoUnit.DAYS.between(a.getStartDate(), a.getEndDate()) + 1;
            totalDays += assignedDays;
            days.merge(a.getCrewUuid(), assignedDays, Long::sum);
            counts.merge(a.getCrewUuid(), 1L, Long::sum);
        }

        List<ResourceUtilisationResponse.CrewUtilisationItem> crews = new ArrayList<>();
        for (UUID crewUuid : days.keySet()) {
            LabourCrew crew = labourCrewRepository.findById(crewUuid).orElse(null);
            crews.add(ResourceUtilisationResponse.CrewUtilisationItem.builder()
                    .crewUuid(crewUuid)
                    .crewName(crew != null ? crew.getName() : "Unknown")
                    .headcount(crew != null ? crew.getHeadcount() : 0)
                    .assignedDays(days.getOrDefault(crewUuid, 0L))
                    .assignmentCount(counts.getOrDefault(crewUuid, 0L))
                    .build());
        }

        return ResourceUtilisationResponse.builder()
                .projectId(project.getId())
                .totalCrewDays(totalDays)
                .assignmentCount(assignments.size())
                .crews(crews)
                .build();
    }

    private CrewAssignmentResponse toResponse(ActivityCrewAssignment a) {
        String crewName = labourCrewRepository.findById(a.getCrewUuid())
                .map(LabourCrew::getName)
                .orElse(null);
        return CrewAssignmentResponse.builder()
                .uuid(a.getUuid())
                .activityUuid(a.getActivityUuid())
                .crewUuid(a.getCrewUuid())
                .crewName(crewName)
                .projectId(a.getProjectId())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .build();
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
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
}
