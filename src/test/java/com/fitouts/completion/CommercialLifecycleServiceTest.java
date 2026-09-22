package com.fitouts.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.fitouts.completion.api.CloseoutChecklistResponse;
import com.fitouts.completion.api.CommerciallyCloseRequest;
import com.fitouts.completion.api.FinalAccountResponse;
import com.fitouts.completion.application.CloseoutChecklistService;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.completion.application.FinalAccountService;
import com.fitouts.completion.domain.CommercialLifecycleStage;
import com.fitouts.completion.domain.ProjectCloseoutChecklist;
import com.fitouts.completion.domain.ProjectCloseoutChecklistRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;

class CommercialLifecycleServiceTest {

    private ProjectService projectService;
    private ProjectCloseoutChecklistRepository checklistRepository;
    private CloseoutChecklistService closeoutChecklistService;
    private FinalAccountService finalAccountService;
    private CommercialLifecycleService service;

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 27L;
    private ProjectCloseoutChecklist storedChecklist;
    private Project project;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        checklistRepository = mock(ProjectCloseoutChecklistRepository.class);
        closeoutChecklistService = mock(CloseoutChecklistService.class);
        finalAccountService = mock(FinalAccountService.class);
        service = new CommercialLifecycleService(
                projectService, checklistRepository, closeoutChecklistService, finalAccountService);

        project = new Project();
        project.setId(projectId);
        project.setCompanyId(companyId);
        project.setName("Lifecycle project");
        project.setStatus("Completed");
        when(projectService.getById(projectId)).thenReturn(project);

        when(checklistRepository.findByProjectIdAndCompanyId(eq(projectId), eq(companyId)))
                .thenAnswer(inv -> Optional.ofNullable(storedChecklist));
        when(checklistRepository.save(any())).thenAnswer(inv -> {
            ProjectCloseoutChecklist row = inv.getArgument(0);
            if (row.getUuid() == null) {
                row.setUuid(UUID.randomUUID());
            }
            storedChecklist = row;
            return row;
        });

        when(closeoutChecklistService.get(projectId)).thenAnswer(inv -> {
            boolean all = storedChecklist != null
                    && storedChecklist.isFinalInvoiceConfirmed()
                    && storedChecklist.isAccountingSynced();
            // For close tests we stub allSatisfied separately via when(...).isAllSatisfied
            return CloseoutChecklistResponse.builder()
                    .projectId(projectId)
                    .allSatisfied(true)
                    .commercialStage(service.resolveStage(storedChecklist, true))
                    .dlpStartDate(storedChecklist != null ? storedChecklist.getDlpStartDate() : null)
                    .dlpEndDate(storedChecklist != null ? storedChecklist.getDlpEndDate() : null)
                    .commerciallyClosedAt(storedChecklist != null ? storedChecklist.getCommerciallyClosedAt() : null)
                    .archivedAt(storedChecklist != null ? storedChecklist.getArchivedAt() : null)
                    .archiveEligible(service.isArchiveEligible(storedChecklist))
                    .build();
        });
        when(finalAccountService.get(projectId)).thenReturn(FinalAccountResponse.builder()
                .projectId(projectId)
                .checklistAllSatisfied(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    private void auth(Role role) {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(10L)
                .companyId(companyId)
                .email(role.name().toLowerCase() + "@test")
                .roles(Set.of(role))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        CompanyContext.set(companyId);
    }

    @Test
    @DisplayName("ADMIN can commercially close")
    void adminCanClose() {
        auth(Role.ADMIN);
        when(closeoutChecklistService.get(projectId)).thenReturn(CloseoutChecklistResponse.builder()
                .projectId(projectId)
                .allSatisfied(true)
                .build());
        CommerciallyCloseRequest request = new CommerciallyCloseRequest();
        request.setDlpStartDate(LocalDate.of(2026, 2, 1));
        request.setDlpEndDate(LocalDate.of(2027, 1, 31));

        CloseoutChecklistResponse response = service.commerciallyClose(projectId, request);

        assertThat(storedChecklist.isCommerciallyClosed()).isTrue();
        assertThat(storedChecklist.getCommerciallyClosedBy()).isEqualTo(10L);
        assertThat(service.resolveStage(storedChecklist, true))
                .isEqualTo(CommercialLifecycleStage.DEFECTS_LIABILITY);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("SUPER_ADMIN can archive when eligible")
    void superAdminCanArchive() {
        auth(Role.SUPER_ADMIN);
        storedChecklist = new ProjectCloseoutChecklist();
        storedChecklist.setUuid(UUID.randomUUID());
        storedChecklist.setProjectId(projectId);
        storedChecklist.setCompanyId(companyId);
        storedChecklist.setCommerciallyClosedAt(java.time.OffsetDateTime.now());
        storedChecklist.setCommerciallyClosedBy(10L);
        storedChecklist.setDlpStartDate(LocalDate.now().minusMonths(13));
        storedChecklist.setDlpEndDate(LocalDate.now().minusDays(1));
        when(closeoutChecklistService.get(projectId)).thenReturn(CloseoutChecklistResponse.builder()
                .projectId(projectId)
                .allSatisfied(true)
                .commercialStage(CommercialLifecycleStage.ARCHIVED)
                .archivedAt(java.time.OffsetDateTime.now())
                .build());

        CloseoutChecklistResponse archived = service.archive(projectId);
        assertThat(storedChecklist.isArchived()).isTrue();
        assertThat(storedChecklist.getArchivedBy()).isEqualTo(10L);
        assertThat(archived.getCommercialStage()).isEqualTo(CommercialLifecycleStage.ARCHIVED);
    }

    @Test
    @DisplayName("PM cannot commercially close")
    void pmCannotClose() {
        auth(Role.PROJECT_MANAGER);
        when(closeoutChecklistService.get(projectId)).thenReturn(CloseoutChecklistResponse.builder()
                .projectId(projectId)
                .allSatisfied(true)
                .build());
        CommerciallyCloseRequest request = new CommerciallyCloseRequest();
        request.setDlpStartDate(LocalDate.now());
        request.setDlpDurationMonths(12);
        assertThatThrownBy(() -> service.commerciallyClose(projectId, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("FINANCE can close when checklist satisfied; enters DLP; status unchanged")
    void financeClosesIntoDlp() {
        auth(Role.FINANCE);
        when(closeoutChecklistService.get(projectId)).thenReturn(CloseoutChecklistResponse.builder()
                .projectId(projectId)
                .allSatisfied(true)
                .build());
        CommerciallyCloseRequest request = new CommerciallyCloseRequest();
        request.setDlpStartDate(LocalDate.of(2026, 1, 1));
        request.setDlpEndDate(LocalDate.of(2026, 12, 31));
        request.setDlpDurationMonths(12);

        CloseoutChecklistResponse response = service.commerciallyClose(projectId, request);

        assertThat(storedChecklist.isCommerciallyClosed()).isTrue();
        assertThat(storedChecklist.getDlpStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(storedChecklist.getDlpEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(service.resolveStage(storedChecklist, true))
                .isEqualTo(CommercialLifecycleStage.DEFECTS_LIABILITY);
        assertThat(project.getStatus()).isEqualTo("Completed");
        assertThat(response).isNotNull();
        assertThat(response.isAllSatisfied()).isTrue();
    }

    @Test
    @DisplayName("cannot close when checklist not satisfied")
    void checklistRequired() {
        auth(Role.BUSINESS_OWNER);
        when(closeoutChecklistService.get(projectId)).thenReturn(CloseoutChecklistResponse.builder()
                .projectId(projectId)
                .allSatisfied(false)
                .build());
        CommerciallyCloseRequest request = new CommerciallyCloseRequest();
        request.setDlpStartDate(LocalDate.now());
        request.setDlpEndDate(LocalDate.now().plusMonths(6));
        assertThatThrownBy(() -> service.commerciallyClose(projectId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("checklist");
    }

    @Test
    @DisplayName("archive rejected before DLP end; succeeds after")
    void archiveGate() {
        auth(Role.BUSINESS_OWNER);
        storedChecklist = new ProjectCloseoutChecklist();
        storedChecklist.setUuid(UUID.randomUUID());
        storedChecklist.setProjectId(projectId);
        storedChecklist.setCompanyId(companyId);
        storedChecklist.setCommerciallyClosedAt(java.time.OffsetDateTime.now());
        storedChecklist.setCommerciallyClosedBy(10L);
        storedChecklist.setDlpStartDate(LocalDate.now().minusMonths(2));
        storedChecklist.setDlpEndDate(LocalDate.now().plusDays(5));

        assertThatThrownBy(() -> service.archive(projectId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DLP end date");

        storedChecklist.setDlpEndDate(LocalDate.now().minusDays(1));
        when(closeoutChecklistService.get(projectId)).thenReturn(CloseoutChecklistResponse.builder()
                .projectId(projectId)
                .allSatisfied(true)
                .commercialStage(CommercialLifecycleStage.ARCHIVED)
                .archivedAt(java.time.OffsetDateTime.now())
                .build());

        CloseoutChecklistResponse archived = service.archive(projectId);
        assertThat(storedChecklist.isArchived()).isTrue();
        assertThat(service.resolveStage(storedChecklist, true)).isEqualTo(CommercialLifecycleStage.ARCHIVED);
        assertThat(archived.getCommercialStage()).isEqualTo(CommercialLifecycleStage.ARCHIVED);
    }

    @Test
    @DisplayName("commercial mutable blocked after close; not archived still allows assertNotArchived")
    void commercialLock() {
        auth(Role.FINANCE);
        storedChecklist = new ProjectCloseoutChecklist();
        storedChecklist.setUuid(UUID.randomUUID());
        storedChecklist.setProjectId(projectId);
        storedChecklist.setCompanyId(companyId);
        storedChecklist.setCommerciallyClosedAt(java.time.OffsetDateTime.now());
        storedChecklist.setDlpStartDate(LocalDate.now());
        storedChecklist.setDlpEndDate(LocalDate.now().plusMonths(12));

        assertThatThrownBy(() -> service.assertCommercialMutable(projectId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("commercially closed");

        service.assertNotArchived(projectId);

        storedChecklist.setArchivedAt(java.time.OffsetDateTime.now());
        assertThatThrownBy(() -> service.assertNotArchived(projectId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("archived");
    }

    @Test
    @DisplayName("ready stage when checklist satisfied and not closed")
    void readyStage() {
        assertThat(service.resolveStage(null, false)).isEqualTo(CommercialLifecycleStage.NOT_READY);
        assertThat(service.resolveStage(null, true)).isEqualTo(CommercialLifecycleStage.READY_FOR_COMMERCIAL_CLOSE);
    }
}
