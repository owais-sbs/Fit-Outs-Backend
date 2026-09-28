package com.fitouts.resource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.planning.application.PlanningService;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.resource.api.ResourceAssignmentRequest;
import com.fitouts.resource.application.ActivityResourceAssignmentService;
import com.fitouts.resource.domain.ActivityResourceAssignment;
import com.fitouts.resource.domain.ActivityResourceAssignmentRepository;
import com.fitouts.resource.domain.ResourceKind;
import com.fitouts.resource.domain.ResourceType;
import com.fitouts.resource.domain.ResourceTypeRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;

class ActivityResourceAssignmentServiceTest {

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 99L;
    private final UUID activityUuid = UUID.randomUUID();
    private final UUID typeUuid = UUID.randomUUID();

    private ActivityResourceAssignmentRepository assignmentRepository;
    private ResourceTypeRepository resourceTypeRepository;
    private ScheduleActivityRepository activityRepository;
    private ProjectService projectService;
    private PlanningService planningService;
    private CommercialLifecycleService commercialLifecycleService;
    private ActivityResourceAssignmentService service;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(ActivityResourceAssignmentRepository.class);
        resourceTypeRepository = mock(ResourceTypeRepository.class);
        activityRepository = mock(ScheduleActivityRepository.class);
        projectService = mock(ProjectService.class);
        planningService = mock(PlanningService.class);
        commercialLifecycleService = mock(CommercialLifecycleService.class);

        service = new ActivityResourceAssignmentService(
                assignmentRepository,
                resourceTypeRepository,
                activityRepository,
                projectService,
                planningService,
                commercialLifecycleService);

        CompanyContext.set(companyId);
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(7L)
                .companyId(companyId)
                .roles(Set.of(Role.ADMIN))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        Project project = new Project();
        project.setId(projectId);
        project.setCompanyId(companyId);
        when(projectService.getById(projectId)).thenReturn(project);

        ScheduleActivity activity = new ScheduleActivity();
        activity.setUuid(activityUuid);
        activity.setProjectId(projectId);
        activity.setCompanyId(companyId);
        when(activityRepository.findByUuidAndCompanyId(activityUuid, companyId))
                .thenReturn(Optional.of(activity));

        ResourceType type = new ResourceType();
        type.setUuid(typeUuid);
        type.setCompanyId(companyId);
        type.setName("Scaffold Tower");
        type.setKind(ResourceKind.PLANT);
        type.setActive(true);
        when(resourceTypeRepository.findByUuidAndCompanyId(typeUuid, companyId))
                .thenReturn(Optional.of(type));
        when(resourceTypeRepository.findById(typeUuid)).thenReturn(Optional.of(type));

        when(assignmentRepository.save(any())).thenAnswer(inv -> {
            ActivityResourceAssignment a = inv.getArgument(0);
            if (a.getUuid() == null) {
                a.setUuid(UUID.randomUUID());
            }
            return a;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    @DisplayName("create rejects overlapping assignment of the same resource type")
    void create_rejectsOverlap() {
        when(assignmentRepository.findOverlapping(
                eq(typeUuid), eq(companyId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(new ActivityResourceAssignment()));

        ResourceAssignmentRequest request = request();

        assertThatThrownBy(() -> service.create(projectId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("overlapping");

        verify(planningService, never()).syncResourceStatus(any(), any(), any());
        verify(planningService, never()).syncLabourStatus(any(), any(), any());
    }

    @Test
    @DisplayName("create syncs resourceStatus only, not labourStatus")
    void create_syncsResourceStatusOnly() {
        when(assignmentRepository.findOverlapping(
                eq(typeUuid), eq(companyId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        service.create(projectId, request());

        verify(planningService).syncResourceStatus(projectId, PlanAreaStatus.IN_PROGRESS, 7L);
        verify(planningService, never()).syncLabourStatus(any(), any(), any());
    }

    @Test
    @DisplayName("create rejects LABOUR resource kinds")
    void create_rejectsLabourKind() {
        ResourceType labour = new ResourceType();
        labour.setUuid(typeUuid);
        labour.setCompanyId(companyId);
        labour.setName("Crew type");
        labour.setKind(ResourceKind.LABOUR);
        labour.setActive(true);
        when(resourceTypeRepository.findByUuidAndCompanyId(typeUuid, companyId))
                .thenReturn(Optional.of(labour));

        assertThatThrownBy(() -> service.create(projectId, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PLANT or TOOL");
    }

    private ResourceAssignmentRequest request() {
        ResourceAssignmentRequest request = new ResourceAssignmentRequest();
        request.setActivityUuid(activityUuid);
        request.setResourceTypeUuid(typeUuid);
        request.setQuantity(2);
        request.setStartDate(LocalDate.of(2026, 3, 1));
        request.setEndDate(LocalDate.of(2026, 3, 5));
        return request;
    }
}
