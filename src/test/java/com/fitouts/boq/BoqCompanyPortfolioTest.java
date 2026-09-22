package com.fitouts.boq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.api.BoqPortfolioProjectResponse;
import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.application.BoqService;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.qto.application.QtoService;
import com.fitouts.qto.domain.QtoLineRepository;
import com.fitouts.roomcollab.application.RoomCollabService;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.security.PortalAccessHelper;
import com.fitouts.variation.domain.ProjectCommercialRepository;
import com.fitouts.workitemconfiguration.domain.WorkItemRepository;

class BoqCompanyPortfolioTest {

    private final UUID companyId = UUID.randomUUID();
    private BoqDocumentRepository boqDocumentRepository;
    private BoqLineRepository boqLineRepository;
    private ProjectService projectService;
    private PortalAccessHelper portalAccess;
    private ProjectCommercialRepository projectCommercialRepository;
    private BoqService boqService;

    @BeforeEach
    void setUp() {
        boqDocumentRepository = mock(BoqDocumentRepository.class);
        boqLineRepository = mock(BoqLineRepository.class);
        projectService = mock(ProjectService.class);
        portalAccess = mock(PortalAccessHelper.class);
        projectCommercialRepository = mock(ProjectCommercialRepository.class);
        boqService = new BoqService(
                boqDocumentRepository,
                boqLineRepository,
                mock(QtoService.class),
                mock(QtoLineRepository.class),
                projectService,
                mock(RoomCollabService.class),
                portalAccess,
                mock(BoqProjectRules.class),
                mock(WorkItemRepository.class),
                projectCommercialRepository
        );
        CompanyContext.set(companyId);
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(1L)
                .companyId(companyId)
                .email("director@test")
                .roles(java.util.Set.of(Role.BUSINESS_OWNER))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    @DisplayName("company-portfolio returns live BOQ totals without loading line items")
    void companyPortfolio_mapsLiveTotalsWithoutLines() {
        Project project = new Project();
        project.setId(18L);
        project.setName("Office tower");
        project.setStatus("In Progress");
        project.setProgress(40);
        project.setBudget(null);

        BoqDocument doc = BoqDocument.builder()
                .id(UUID.randomUUID())
                .project(project)
                .companyId(companyId)
                .version("1.0")
                .status(BoqDocumentStatus.PENDING_DIRECTOR)
                .subtotal(new BigDecimal("480.00"))
                .vatAmount(new BigDecimal("24.00"))
                .grandTotal(new BigDecimal("504.00"))
                .build();
        doc.setCreatedAt(LocalDateTime.now());

        when(projectService.getAll()).thenReturn(List.of(project));
        when(boqDocumentRepository.findAllWithProjectByCompanyId(companyId)).thenReturn(List.of(doc));
        when(projectCommercialRepository.findByCompanyId(companyId)).thenReturn(List.of());

        List<BoqPortfolioProjectResponse> rows = boqService.listCompanyPortfolio();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getProjectId()).isEqualTo(18L);
        assertThat(rows.get(0).getBoqTotal()).isEqualByComparingTo("504.00");
        assertThat(rows.get(0).getGrandTotal()).isEqualByComparingTo("504.00");
        assertThat(rows.get(0).getBudget()).isEqualByComparingTo("504.00");
        assertThat(rows.get(0).getBoqs()).hasSize(1);
        verify(boqLineRepository, never()).findByBoqIdOrderBySortOrderAsc(doc.getId());
        verify(boqDocumentRepository, never()).findByProjectIdOrderByCreatedAtDesc(18L);
    }
}
