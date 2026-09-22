package com.fitouts.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import com.fitouts.completion.api.CloseoutCarrySnagsRequest;
import com.fitouts.completion.api.CloseoutChecklistResponse;
import com.fitouts.completion.api.CloseoutChecklistResponse.Item;
import com.fitouts.completion.api.CloseoutConfirmRequest;
import com.fitouts.completion.application.CloseoutChecklistService;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.completion.domain.CommercialLifecycleStage;
import com.fitouts.completion.domain.ProjectCloseoutCarriedSnag;
import com.fitouts.completion.domain.ProjectCloseoutCarriedSnagRepository;
import com.fitouts.completion.domain.ProjectCloseoutChecklist;
import com.fitouts.completion.domain.ProjectCloseoutChecklistRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.snag.domain.Snag;
import com.fitouts.snag.domain.SnagRepository;
import com.fitouts.snag.domain.SnagStatus;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.variation.domain.VariationStatus;

class CloseoutChecklistServiceTest {

    private ProjectService projectService;
    private VariationRequestRepository variationRepository;
    private SnagRepository snagRepository;
    private ProjectCloseoutChecklistRepository checklistRepository;
    private ProjectCloseoutCarriedSnagRepository carriedSnagRepository;
    private CommercialLifecycleService commercialLifecycleService;
    private CloseoutChecklistService service;

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 27L;
    private final List<VariationRequest> variations = new ArrayList<>();
    private final List<Snag> snags = new ArrayList<>();
    private final List<ProjectCloseoutCarriedSnag> carried = new ArrayList<>();
    private ProjectCloseoutChecklist storedChecklist;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        variationRepository = mock(VariationRequestRepository.class);
        snagRepository = mock(SnagRepository.class);
        checklistRepository = mock(ProjectCloseoutChecklistRepository.class);
        carriedSnagRepository = mock(ProjectCloseoutCarriedSnagRepository.class);
        commercialLifecycleService = mock(CommercialLifecycleService.class);
        when(commercialLifecycleService.resolveStage(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(inv -> {
                    ProjectCloseoutChecklist cl = inv.getArgument(0);
                    boolean allSatisfied = inv.getArgument(1);
                    if (cl != null && cl.isArchived()) {
                        return CommercialLifecycleStage.ARCHIVED;
                    }
                    if (cl != null && cl.isCommerciallyClosed()) {
                        return CommercialLifecycleStage.DEFECTS_LIABILITY;
                    }
                    return allSatisfied
                            ? CommercialLifecycleStage.READY_FOR_COMMERCIAL_CLOSE
                            : CommercialLifecycleStage.NOT_READY;
                });
        when(commercialLifecycleService.isArchiveEligible(any())).thenReturn(false);
        org.mockito.Mockito.doNothing().when(commercialLifecycleService).assertCommercialMutable(any());
        service = new CloseoutChecklistService(
                projectService, variationRepository, snagRepository,
                checklistRepository, carriedSnagRepository, commercialLifecycleService);

        Project project = new Project();
        project.setId(projectId);
        project.setCompanyId(companyId);
        project.setName("Close-out project");
        when(projectService.getById(projectId)).thenReturn(project);

        when(variationRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId))
                .thenAnswer(inv -> List.copyOf(variations));
        when(snagRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId))
                .thenAnswer(inv -> List.copyOf(snags));
        when(checklistRepository.findByProjectIdAndCompanyId(projectId, companyId))
                .thenAnswer(inv -> Optional.ofNullable(storedChecklist));
        when(checklistRepository.save(any())).thenAnswer(inv -> {
            ProjectCloseoutChecklist row = inv.getArgument(0);
            if (row.getUuid() == null) row.setUuid(UUID.randomUUID());
            storedChecklist = row;
            return row;
        });
        when(carriedSnagRepository.findByChecklistUuid(any())).thenAnswer(inv -> {
            UUID checklistUuid = inv.getArgument(0);
            return carried.stream().filter(c -> checklistUuid.equals(c.getChecklistUuid())).toList();
        });
        when(carriedSnagRepository.save(any())).thenAnswer(inv -> {
            ProjectCloseoutCarriedSnag row = inv.getArgument(0);
            if (row.getUuid() == null) row.setUuid(UUID.randomUUID());
            carried.removeIf(c -> c.getSnagUuid().equals(row.getSnagUuid())
                    && c.getChecklistUuid().equals(row.getChecklistUuid()));
            carried.add(row);
            return row;
        });
        org.mockito.Mockito.doAnswer(inv -> {
            UUID checklistUuid = inv.getArgument(0);
            carried.removeIf(c -> checklistUuid.equals(c.getChecklistUuid()));
            return null;
        }).when(carriedSnagRepository).deleteByChecklistUuid(any());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    private void auth(Role role, Long accountId) {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(accountId)
                .companyId(companyId)
                .email(role.name().toLowerCase() + "@test")
                .roles(Set.of(role))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        CompanyContext.set(companyId);
    }

    private VariationRequest variation(VariationStatus status) {
        VariationRequest vr = new VariationRequest();
        vr.setUuid(UUID.randomUUID());
        vr.setProjectId(projectId);
        vr.setCompanyId(companyId);
        vr.setCrNumber("CR-" + status.name());
        vr.setTitle("Variation " + status.name());
        vr.setStatus(status);
        variations.add(vr);
        return vr;
    }

    private Snag snag(SnagStatus status) {
        Snag snag = new Snag();
        snag.setUuid(UUID.randomUUID());
        snag.setProjectId(projectId);
        snag.setCompanyId(companyId);
        snag.setTitle("Snag " + status.name());
        snag.setStatus(status);
        snags.add(snag);
        return snag;
    }

    private Item item(CloseoutChecklistResponse response, String key) {
        return response.getItems().stream()
                .filter(i -> key.equals(i.getKey()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("empty project: auto items satisfied, manuals outstanding")
    void emptyProjectReadyExceptManuals() {
        auth(Role.PROJECT_MANAGER, 5L);
        CloseoutChecklistResponse response = service.get(projectId);
        assertThat(response.isAllSatisfied()).isFalse();
        assertThat(response.getOutstandingCount()).isEqualTo(2);
        assertThat(response.getSummary()).contains("two things");
        assertThat(item(response, "VARIATIONS").isSatisfied()).isTrue();
        assertThat(item(response, "SNAGS").isSatisfied()).isTrue();
        assertThat(item(response, "FINAL_INVOICE").isSatisfied()).isFalse();
        assertThat(item(response, "ACCOUNTING").isSatisfied()).isFalse();
        assertThat(item(response, "VARIATIONS").getEvaluation()).isEqualTo("AUTOMATIC");
        assertThat(item(response, "FINAL_INVOICE").getEvaluation()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("open client variation blocks close-out")
    void openVariationBlocks() {
        auth(Role.ADMIN, 1L);
        variation(VariationStatus.ISSUED_TO_CLIENT);
        variation(VariationStatus.APPROVED);
        CloseoutChecklistResponse response = service.get(projectId);
        Item variationsItem = item(response, "VARIATIONS");
        assertThat(variationsItem.isSatisfied()).isFalse();
        assertThat(variationsItem.getOutstandingCount()).isEqualTo(1);
        assertThat(variationsItem.getEntries()).hasSize(1);
        assertThat(variationsItem.getEntries().get(0).getStatus()).isEqualTo("ISSUED_TO_CLIENT");
    }

    @Test
    @DisplayName("approved and rejected variations count as closed")
    void terminalVariationsSatisfied() {
        auth(Role.QS, 8L);
        variation(VariationStatus.APPROVED);
        variation(VariationStatus.REJECTED);
        CloseoutChecklistResponse response = service.get(projectId);
        assertThat(item(response, "VARIATIONS").isSatisfied()).isTrue();
        assertThat(item(response, "VARIATIONS").getOutstandingCount()).isZero();
    }

    @Test
    @DisplayName("resolved snag blocks until closed or carried forward")
    void resolvedSnagBlocks() {
        auth(Role.PROJECT_MANAGER, 5L);
        snag(SnagStatus.RESOLVED);
        snag(SnagStatus.CLOSED);
        CloseoutChecklistResponse response = service.get(projectId);
        Item snagsItem = item(response, "SNAGS");
        assertThat(snagsItem.isSatisfied()).isFalse();
        assertThat(snagsItem.getOutstandingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("carrying remaining snags forward satisfies the snag item")
    void carryForwardSatisfiesSnags() {
        auth(Role.PROJECT_MANAGER, 5L);
        Snag open = snag(SnagStatus.OPEN);
        CloseoutCarrySnagsRequest request = new CloseoutCarrySnagsRequest();
        request.setSnagUuids(List.of(open.getUuid()));
        CloseoutChecklistResponse response = service.carrySnags(projectId, request);
        Item snagsItem = item(response, "SNAGS");
        assertThat(snagsItem.isSatisfied()).isTrue();
        assertThat(snagsItem.getCarriedForwardCount()).isEqualTo(1);
        assertThat(snagsItem.getEntries().get(0).isCarriedForward()).isTrue();
    }

    @Test
    @DisplayName("cannot carry a snag that is not on the project")
    void carryUnknownSnagRejected() {
        auth(Role.PROJECT_MANAGER, 5L);
        CloseoutCarrySnagsRequest request = new CloseoutCarrySnagsRequest();
        request.setSnagUuids(List.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.carrySnags(projectId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not on this project");
    }

    @Test
    @DisplayName("cannot carry a closed snag")
    void carryClosedSnagRejected() {
        auth(Role.PROJECT_MANAGER, 5L);
        Snag closed = snag(SnagStatus.CLOSED);
        CloseoutCarrySnagsRequest request = new CloseoutCarrySnagsRequest();
        request.setSnagUuids(List.of(closed.getUuid()));
        assertThatThrownBy(() -> service.carrySnags(projectId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Closed snags");
    }

    @Test
    @DisplayName("confirming both manuals on a clean project makes close-out ready")
    void confirmingManualsCompletesChecklist() {
        auth(Role.FINANCE, 9L);
        CloseoutConfirmRequest confirm = new CloseoutConfirmRequest();
        confirm.setConfirmed(true);
        service.confirmFinalInvoice(projectId, confirm);
        CloseoutChecklistResponse response = service.confirmAccounting(projectId, confirm);
        assertThat(response.isAllSatisfied()).isTrue();
        assertThat(response.getOutstandingCount()).isZero();
        assertThat(response.getSummary()).startsWith("This project is ready to close");
        assertThat(item(response, "FINAL_INVOICE").getConfirmedBy()).isEqualTo(9L);
        assertThat(item(response, "ACCOUNTING").getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("unconfirming a manual item reopens close-out")
    void unconfirmReopens() {
        auth(Role.ADMIN, 1L);
        CloseoutConfirmRequest confirm = new CloseoutConfirmRequest();
        confirm.setConfirmed(true);
        service.confirmFinalInvoice(projectId, confirm);
        service.confirmAccounting(projectId, confirm);
        CloseoutConfirmRequest undo = new CloseoutConfirmRequest();
        undo.setConfirmed(false);
        CloseoutChecklistResponse response = service.confirmFinalInvoice(projectId, undo);
        assertThat(response.isAllSatisfied()).isFalse();
        assertThat(item(response, "FINAL_INVOICE").isSatisfied()).isFalse();
        assertThat(item(response, "ACCOUNTING").isSatisfied()).isTrue();
        assertThat(response.getSummary()).contains("one thing");
    }

    @Test
    @DisplayName("pure client cannot read the close-out checklist")
    void clientForbidden() {
        auth(Role.CLIENT, 99L);
        assertThatThrownBy(() -> service.get(projectId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("summary uses the process wording from the module brief")
    void summaryWording() {
        assertThat(CloseoutChecklistService.buildSummary(0))
                .isEqualTo("This project is ready to close. All close-out requirements have been satisfied.");
        assertThat(CloseoutChecklistService.buildSummary(1))
                .contains("one thing that needs attention");
        assertThat(CloseoutChecklistService.buildSummary(2))
                .contains("two things that need attention");
    }
}
