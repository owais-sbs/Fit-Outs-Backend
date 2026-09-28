package com.fitouts.resource.application;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.planning.application.PlanningService;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.resource.api.PlantToolUtilisationResponse;
import com.fitouts.resource.api.ResourceAssignmentRequest;
import com.fitouts.resource.api.ResourceAssignmentResponse;
import com.fitouts.resource.domain.ActivityResourceAssignment;
import com.fitouts.resource.domain.ActivityResourceAssignmentRepository;
import com.fitouts.resource.domain.ResourceKind;
import com.fitouts.resource.domain.ResourceType;
import com.fitouts.resource.domain.ResourceTypeRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivityResourceAssignmentService {

    private static final Set<ResourceKind> ASSIGNABLE_KINDS = EnumSet.of(ResourceKind.PLANT, ResourceKind.TOOL);

    private final ActivityResourceAssignmentRepository assignmentRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ProjectService projectService;
    private final PlanningService planningService;
    private final CommercialLifecycleService commercialLifecycleService;

    @Transactional(readOnly = true)
    public List<ResourceAssignmentResponse> list(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        return assignmentRepository.findByProjectIdAndCompanyIdOrderByStartDateAsc(project.getId(), companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ResourceAssignmentResponse create(Long projectId, ResourceAssignmentRequest request) {
        AuthPrincipal principal = requireStaff();
        commercialLifecycleService.assertNotArchived(projectId);
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        if (request.getActivityUuid() == null || request.getResourceTypeUuid() == null) {
            throw new BadRequestException("activityUuid and resourceTypeUuid are required");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        if (quantity < 1) {
            throw new BadRequestException("quantity must be at least 1");
        }

        ScheduleActivity activity = activityRepository
                .findByUuidAndCompanyId(request.getActivityUuid(), companyId)
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        if (!activity.getProjectId().equals(project.getId())) {
            throw new BadRequestException("Activity does not belong to this project");
        }

        ResourceType type = resourceTypeRepository
                .findByUuidAndCompanyId(request.getResourceTypeUuid(), companyId)
                .orElseThrow(() -> new NotFoundException("Resource type not found"));
        if (!type.isActive()) {
            throw new BadRequestException("Resource type is inactive");
        }
        if (!ASSIGNABLE_KINDS.contains(type.getKind())) {
            throw new BadRequestException("Only PLANT or TOOL resource types can be assigned");
        }

        List<ActivityResourceAssignment> overlaps = assignmentRepository.findOverlapping(
                type.getUuid(), companyId, request.getStartDate(), request.getEndDate());
        if (!overlaps.isEmpty()) {
            throw new BadRequestException("Resource type is already assigned on overlapping dates");
        }

        ActivityResourceAssignment assignment = new ActivityResourceAssignment();
        assignment.setActivityUuid(activity.getUuid());
        assignment.setResourceTypeUuid(type.getUuid());
        assignment.setProjectId(project.getId());
        assignment.setCompanyId(companyId);
        assignment.setQuantity(quantity);
        assignment.setStartDate(request.getStartDate());
        assignment.setEndDate(request.getEndDate());
        assignment = assignmentRepository.save(assignment);

        planningService.syncResourceStatus(project.getId(), PlanAreaStatus.IN_PROGRESS, principal.getAccountId());
        return toResponse(assignment);
    }

    @Transactional
    public void delete(Long projectId, UUID assignmentUuid) {
        AuthPrincipal principal = requireStaff();
        commercialLifecycleService.assertNotArchived(projectId);
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        ActivityResourceAssignment assignment = assignmentRepository
                .findByUuidAndCompanyId(assignmentUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Resource assignment not found"));
        if (!assignment.getProjectId().equals(project.getId())) {
            throw new BadRequestException("Assignment does not belong to this project");
        }
        assignmentRepository.delete(assignment);

        long remaining = assignmentRepository.countByProjectIdAndCompanyId(project.getId(), companyId);
        if (remaining == 0) {
            planningService.syncResourceStatus(project.getId(), PlanAreaStatus.NOT_STARTED, principal.getAccountId());
        }
    }

    @Transactional(readOnly = true)
    public PlantToolUtilisationResponse utilisation(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        List<ActivityResourceAssignment> assignments = assignmentRepository
                .findByProjectIdAndCompanyIdOrderByStartDateAsc(project.getId(), companyId);

        Map<UUID, Long> days = new HashMap<>();
        Map<UUID, Long> quantityDays = new HashMap<>();
        Map<UUID, Long> counts = new HashMap<>();
        long totalDays = 0;

        for (ActivityResourceAssignment a : assignments) {
            long assignedDays = ChronoUnit.DAYS.between(a.getStartDate(), a.getEndDate()) + 1;
            long qtyDays = assignedDays * a.getQuantity();
            totalDays += qtyDays;
            days.merge(a.getResourceTypeUuid(), assignedDays, Long::sum);
            quantityDays.merge(a.getResourceTypeUuid(), qtyDays, Long::sum);
            counts.merge(a.getResourceTypeUuid(), 1L, Long::sum);
        }

        List<PlantToolUtilisationResponse.TypeUtilisationItem> resources = new ArrayList<>();
        for (UUID typeUuid : days.keySet()) {
            ResourceType type = resourceTypeRepository.findById(typeUuid).orElse(null);
            resources.add(PlantToolUtilisationResponse.TypeUtilisationItem.builder()
                    .resourceTypeUuid(typeUuid)
                    .resourceTypeName(type != null ? type.getName() : "Unknown")
                    .kind(type != null ? type.getKind() : null)
                    .assignedDays(days.getOrDefault(typeUuid, 0L))
                    .quantityDays(quantityDays.getOrDefault(typeUuid, 0L))
                    .assignmentCount(counts.getOrDefault(typeUuid, 0L))
                    .build());
        }

        return PlantToolUtilisationResponse.builder()
                .projectId(project.getId())
                .totalResourceDays(totalDays)
                .assignmentCount(assignments.size())
                .resources(resources)
                .build();
    }

    private ResourceAssignmentResponse toResponse(ActivityResourceAssignment a) {
        ResourceType type = resourceTypeRepository.findById(a.getResourceTypeUuid()).orElse(null);
        return ResourceAssignmentResponse.builder()
                .uuid(a.getUuid())
                .activityUuid(a.getActivityUuid())
                .resourceTypeUuid(a.getResourceTypeUuid())
                .resourceTypeName(type != null ? type.getName() : null)
                .kind(type != null ? type.getKind() : null)
                .quantity(a.getQuantity())
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
