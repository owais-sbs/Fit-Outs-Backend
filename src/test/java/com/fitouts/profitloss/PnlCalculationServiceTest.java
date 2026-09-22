package com.fitouts.profitloss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.procurement.domain.StockMovementRepository;
import com.fitouts.profitloss.application.OverheadAllocationService;
import com.fitouts.profitloss.application.PnlCalculationService;
import com.fitouts.profitloss.application.PnlExportService;
import com.fitouts.profitloss.domain.OverheadBasis;
import com.fitouts.profitloss.domain.OverheadRule;
import com.fitouts.profitloss.domain.OverheadRuleRepository;
import com.fitouts.profitloss.domain.ProjectPnlSnapshot;
import com.fitouts.profitloss.domain.ProjectPnlSnapshotRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.StockMovementType;
import com.fitouts.subcontractor.domain.ScPaymentCertificate;
import com.fitouts.subcontractor.domain.ScPaymentCertificateRepository;
import com.fitouts.subcontractor.domain.ScPaymentCertificateStatus;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;

class PnlCalculationServiceTest {

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 42L;

    private ProjectPnlSnapshotRepository snapshotRepository;
    private ProjectCommercialRepository commercialRepository;
    private StockMovementRepository stockMovementRepository;
    private ScPaymentCertificateRepository certificateRepository;
    private ProjectRepository projectRepository;
    private OverheadRuleRepository overheadRuleRepository;
    private PnlCalculationService service;
    private PnlExportService exportService;

    @BeforeEach
    void setUp() {
        snapshotRepository = mock(ProjectPnlSnapshotRepository.class);
        commercialRepository = mock(ProjectCommercialRepository.class);
        stockMovementRepository = mock(StockMovementRepository.class);
        certificateRepository = mock(ScPaymentCertificateRepository.class);
        projectRepository = mock(ProjectRepository.class);
        overheadRuleRepository = mock(OverheadRuleRepository.class);

        OverheadAllocationService overheadAllocationService =
                new OverheadAllocationService(overheadRuleRepository);
        service = new PnlCalculationService(
                snapshotRepository,
                commercialRepository,
                stockMovementRepository,
                certificateRepository,
                projectRepository,
                overheadAllocationService);
        exportService = new PnlExportService(service);

        CompanyContext.set(companyId);
        auth(Role.FINANCE);

        Project project = new Project();
        project.setId(projectId);
        project.setCompanyId(companyId);
        project.setName("Tower Fit-Out");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.findByCompanyIdAndIsDeletedFalse(companyId)).thenReturn(List.of(project));

        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            ProjectPnlSnapshot s = inv.getArgument(0);
            if (s.getUuid() == null) {
                s.setUuid(UUID.randomUUID());
            }
            return s;
        });
        when(snapshotRepository.findByCompanyIdAndProjectIdAndPeriodYearMonth(eq(companyId), eq(projectId), any()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findFirstByCompanyIdAndProjectIdOrderByCalculatedAtDesc(companyId, projectId))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findByCompanyIdAndPeriodYearMonthOrderByProjectIdAsc(eq(companyId), any()))
                .thenAnswer(inv -> {
                    ProjectPnlSnapshot snap = service.recalculate(projectId, companyId);
                    return List.of(snap);
                });
    }

    @Test
    @DisplayName("recalculate sums SC + materials + variation + overhead; labour is zero")
    void recalculate_costModel() {
        ProjectCommercial commercial = new ProjectCommercial();
        commercial.setCurrentContractValue(new BigDecimal("1000000"));
        commercial.setCurrentCost(new BigDecimal("25000"));
        commercial.setOriginalContractValue(new BigDecimal("900000"));
        commercial.setOriginalCost(new BigDecimal("400000"));
        when(commercialRepository.findByProjectIdAndCompanyId(projectId, companyId))
                .thenReturn(Optional.of(commercial));

        when(stockMovementRepository.sumTotalCostByProjectAndType(
                companyId, projectId, StockMovementType.ISSUE))
                .thenReturn(new BigDecimal("50000"));

        ScPaymentCertificate cert = new ScPaymentCertificate();
        cert.setCertifiedValue(new BigDecimal("120000"));
        cert.setStatus(ScPaymentCertificateStatus.ISSUED);
        when(certificateRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId))
                .thenReturn(List.of(cert));

        OverheadRule rule = new OverheadRule();
        rule.setCompanyId(companyId);
        rule.setPercentage(new BigDecimal("5"));
        rule.setBasis(OverheadBasis.CONTRACT_VALUE);
        rule.setActive(true);
        when(overheadRuleRepository.findFirstByCompanyIdAndActiveTrueOrderByUpdatedAtDesc(companyId))
                .thenReturn(Optional.of(rule));

        ProjectPnlSnapshot snap = service.recalculate(projectId, companyId);

        assertThat(snap.getContractValue()).isEqualByComparingTo("1000000");
        assertThat(snap.getMaterialCost()).isEqualByComparingTo("50000");
        assertThat(snap.getScCertifiedCost()).isEqualByComparingTo("120000");
        assertThat(snap.getVariationCost()).isEqualByComparingTo("25000");
        assertThat(snap.getOverheadAllocated()).isEqualByComparingTo("50000"); // 5% of 1M
        assertThat(snap.getLabourCost()).isEqualByComparingTo("0");
        // total = 50k + 0 + 120k + 25k + 50k = 245k
        assertThat(snap.getTotalCost()).isEqualByComparingTo("245000");
        assertThat(snap.getMargin()).isEqualByComparingTo("755000");
        assertThat(snap.getMarginVsOriginalEstimate()).isNotNull();
    }

    @Test
    @DisplayName("CSV export contains expected columns and project row")
    void export_csv() {
        when(commercialRepository.findByProjectIdAndCompanyId(projectId, companyId))
                .thenReturn(Optional.empty());
        when(stockMovementRepository.sumTotalCostByProjectAndType(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(certificateRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(overheadRuleRepository.findFirstByCompanyIdAndActiveTrueOrderByUpdatedAtDesc(companyId))
                .thenReturn(Optional.empty());

        String csv = exportService.companyCsv(null);
        assertThat(csv).contains("period,projectId,projectName");
        assertThat(csv).contains("Tower Fit-Out");
    }

    @Test
    @DisplayName("PDF export returns non-empty PDF bytes")
    void export_pdf() {
        when(commercialRepository.findByProjectIdAndCompanyId(projectId, companyId))
                .thenReturn(Optional.empty());
        when(stockMovementRepository.sumTotalCostByProjectAndType(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(certificateRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(overheadRuleRepository.findFirstByCompanyIdAndActiveTrueOrderByUpdatedAtDesc(companyId))
                .thenReturn(Optional.empty());

        byte[] pdf = exportService.companyPdf(null);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, Math.min(8, pdf.length))).startsWith("%PDF");
    }

    private void auth(Role role) {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(1L)
                .companyId(companyId)
                .roles(java.util.Set.of(role))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
