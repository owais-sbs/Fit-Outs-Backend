package com.fitouts.completion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.fitouts.billing.domain.BillingMilestone;
import com.fitouts.billing.domain.BillingMilestoneRepository;
import com.fitouts.billing.domain.BillingStatus;
import com.fitouts.billing.domain.PaymentRequest;
import com.fitouts.billing.domain.PaymentRequestRepository;
import com.fitouts.boq.application.BoqProjectRules;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.completion.api.CloseoutChecklistResponse;
import com.fitouts.completion.api.CloseoutChecklistResponse.Item;
import com.fitouts.completion.api.FinalAccountResponse;
import com.fitouts.completion.application.CloseoutChecklistService;
import com.fitouts.completion.application.FinalAccountService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.variation.domain.VariationStatus;

class FinalAccountServiceTest {

    private ProjectService projectService;
    private BoqProjectRules boqProjectRules;
    private ProjectCommercialRepository commercialRepository;
    private VariationRequestRepository variationRepository;
    private BillingMilestoneRepository milestoneRepository;
    private PaymentRequestRepository paymentRequestRepository;
    private CloseoutChecklistService closeoutChecklistService;
    private FinalAccountService service;

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 27L;
    private final List<VariationRequest> variations = new ArrayList<>();
    private final List<BillingMilestone> milestones = new ArrayList<>();
    private final List<PaymentRequest> paymentRequests = new ArrayList<>();
    private ProjectCommercial commercial;
    private BoqDocument approvedBoq;
    private CloseoutChecklistResponse checklist;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        boqProjectRules = mock(BoqProjectRules.class);
        commercialRepository = mock(ProjectCommercialRepository.class);
        variationRepository = mock(VariationRequestRepository.class);
        milestoneRepository = mock(BillingMilestoneRepository.class);
        paymentRequestRepository = mock(PaymentRequestRepository.class);
        closeoutChecklistService = mock(CloseoutChecklistService.class);
        service = new FinalAccountService(
                projectService,
                boqProjectRules,
                commercialRepository,
                variationRepository,
                milestoneRepository,
                paymentRequestRepository,
                closeoutChecklistService);

        Project project = new Project();
        project.setId(projectId);
        project.setCompanyId(companyId);
        project.setName("Final account project");
        when(projectService.getById(projectId)).thenReturn(project);

        when(variationRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId))
                .thenAnswer(inv -> List.copyOf(variations));
        when(commercialRepository.findByProjectIdAndCompanyId(projectId, companyId))
                .thenAnswer(inv -> Optional.ofNullable(commercial));
        when(boqProjectRules.findApproved(projectId))
                .thenAnswer(inv -> Optional.ofNullable(approvedBoq));
        when(milestoneRepository.findByProjectIdAndCompanyIdOrderByDueDateAscCreatedAtAsc(projectId, companyId))
                .thenAnswer(inv -> List.copyOf(milestones));
        when(paymentRequestRepository.findByMilestoneUuidAndCompanyIdOrderByCreatedAtDesc(any(), eq(companyId)))
                .thenAnswer(inv -> {
                    UUID milestoneUuid = inv.getArgument(0);
                    return paymentRequests.stream()
                            .filter(pr -> milestoneUuid.equals(pr.getMilestoneUuid()))
                            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                            .toList();
                });

        checklist = CloseoutChecklistResponse.builder()
                .projectId(projectId)
                .allSatisfied(false)
                .satisfiedCount(2)
                .outstandingCount(2)
                .summary("Almost ready")
                .items(List.of(
                        Item.builder()
                                .key(CloseoutChecklistService.KEY_VARIATIONS)
                                .title("Variations are closed")
                                .satisfied(true)
                                .detail("All variations are approved or rejected.")
                                .build(),
                        Item.builder()
                                .key(CloseoutChecklistService.KEY_FINAL_INVOICE)
                                .title("Final invoice has been issued")
                                .satisfied(false)
                                .detail("Confirm once issued")
                                .build(),
                        Item.builder()
                                .key(CloseoutChecklistService.KEY_ACCOUNTING)
                                .title("Accounting has been synchronized")
                                .satisfied(false)
                                .detail("Confirm once synced")
                                .build()))
                .build();
        when(closeoutChecklistService.get(projectId)).thenReturn(checklist);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    private void auth(Role role) {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(1L)
                .companyId(companyId)
                .email(role.name().toLowerCase() + "@test")
                .roles(Set.of(role))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        CompanyContext.set(companyId);
    }

    private VariationRequest approvedVariation(String cr, String title, String sell) {
        VariationRequest vr = new VariationRequest();
        vr.setUuid(UUID.randomUUID());
        vr.setProjectId(projectId);
        vr.setCompanyId(companyId);
        vr.setCrNumber(cr);
        vr.setTitle(title);
        vr.setStatus(VariationStatus.APPROVED);
        vr.setSellDelta(new BigDecimal(sell));
        variations.add(vr);
        return vr;
    }

    private BillingMilestone milestone(String amount) {
        BillingMilestone m = new BillingMilestone();
        m.setUuid(UUID.randomUUID());
        m.setProjectId(projectId);
        m.setCompanyId(companyId);
        m.setName("Milestone");
        m.setAmount(new BigDecimal(amount));
        m.setStatus(BillingStatus.DRAFT);
        milestones.add(m);
        return m;
    }

    private PaymentRequest payment(BillingMilestone milestone, BillingStatus status, String amount, int createdOffsetSeconds) {
        PaymentRequest pr = new PaymentRequest();
        pr.setUuid(UUID.randomUUID());
        pr.setMilestoneUuid(milestone.getUuid());
        pr.setProjectId(projectId);
        pr.setCompanyId(companyId);
        pr.setStatus(status);
        pr.setAmount(new BigDecimal(amount));
        pr.setCreatedAt(java.time.OffsetDateTime.now().minusSeconds(createdOffsetSeconds));
        pr.setUpdatedAt(pr.getCreatedAt());
        paymentRequests.add(pr);
        return pr;
    }

    @Test
    @DisplayName("Sample arithmetic: 1M contract, 800k issued, 700k paid → 100k outstanding, 200k unbilled")
    void billingArithmeticMatchesExample() {
        auth(Role.ADMIN);
        approvedBoq = new BoqDocument();
        approvedBoq.setGrandTotal(new BigDecimal("1000000"));

        commercial = new ProjectCommercial();
        commercial.setCurrentContractValue(new BigDecimal("1000000"));

        BillingMilestone paidA = milestone("500000");
        BillingMilestone paidB = milestone("200000");
        BillingMilestone issued = milestone("100000");
        milestone("200000"); // scheduled but no issued payment request → unbilled
        payment(paidA, BillingStatus.PAID, "500000", 4);
        payment(paidB, BillingStatus.PAID, "200000", 3);
        payment(issued, BillingStatus.ISSUED, "100000", 2);

        FinalAccountResponse response = service.get(projectId);

        assertThat(response.getContract().getFinalContractValue()).isEqualByComparingTo("1000000");
        assertThat(response.getBilling().getTotalScheduled()).isEqualByComparingTo("1000000");
        assertThat(response.getBilling().getTotalIssued()).isEqualByComparingTo("800000");
        assertThat(response.getBilling().getTotalPaid()).isEqualByComparingTo("700000");
        assertThat(response.getBilling().getOutstanding()).isEqualByComparingTo("100000");
        assertThat(response.getBilling().getUnbilledRemaining()).isEqualByComparingTo("200000");
        assertThat(response.getBilling().getTotalIssued())
                .isEqualByComparingTo(response.getBilling().getTotalPaid().add(response.getBilling().getOutstanding()));
    }

    @Test
    @DisplayName("Contract uses BOQ original, approved variation sum, and project_commercial final when present")
    void contractAndApprovedVariationBreakdown() {
        auth(Role.PROJECT_MANAGER);
        approvedBoq = new BoqDocument();
        approvedBoq.setGrandTotal(new BigDecimal("2000000"));

        approvedVariation("CR-001", "Additional flooring", "50000");
        approvedVariation("CR-003", "Electrical changes", "80000");
        approvedVariation("CR-005", "Design change", "50000");

        VariationRequest draft = new VariationRequest();
        draft.setUuid(UUID.randomUUID());
        draft.setCrNumber("CR-099");
        draft.setTitle("Open draft");
        draft.setStatus(VariationStatus.DRAFT);
        draft.setSellDelta(new BigDecimal("99999"));
        variations.add(draft);

        commercial = new ProjectCommercial();
        commercial.setOriginalContractValue(new BigDecimal("2000000"));
        commercial.setCurrentContractValue(new BigDecimal("2180000"));

        FinalAccountResponse response = service.get(projectId);

        assertThat(response.getContract().isHasApprovedBoq()).isTrue();
        assertThat(response.getContract().isUsingProjectCommercial()).isTrue();
        assertThat(response.getContract().getOriginalContractValue()).isEqualByComparingTo("2000000");
        assertThat(response.getContract().getApprovedVariationsTotal()).isEqualByComparingTo("180000");
        assertThat(response.getContract().getFinalContractValue()).isEqualByComparingTo("2180000");

        assertThat(response.getVariations().getLines()).hasSize(3);
        assertThat(response.getVariations().getLines())
                .extracting(FinalAccountResponse.VariationLine::getCrNumber)
                .containsExactly("CR-001", "CR-003", "CR-005");
        assertThat(response.getVariations().isCloseoutSatisfied()).isTrue();
        assertThat(response.getCloseOut().isFinalInvoiceConfirmed()).isFalse();
        assertThat(response.getCloseOut().isAccountingSynced()).isFalse();
    }

    @Test
    @DisplayName("Without project_commercial, final contract = original + approved variations")
    void finalContractFallsBackWithoutCommercial() {
        auth(Role.FINANCE);
        approvedBoq = new BoqDocument();
        approvedBoq.setGrandTotal(new BigDecimal("1000000"));
        approvedVariation("CR-001", "Extra work", "150000");
        commercial = null;

        FinalAccountResponse response = service.get(projectId);

        assertThat(response.getContract().isUsingProjectCommercial()).isFalse();
        assertThat(response.getContract().getFinalContractValue()).isEqualByComparingTo("1150000");
    }

    @Test
    @DisplayName("Without approved BOQ, original is zero and hasApprovedBoq is false")
    void noApprovedBoq() {
        auth(Role.ADMIN);
        approvedBoq = null;
        commercial = null;

        FinalAccountResponse response = service.get(projectId);

        assertThat(response.getContract().isHasApprovedBoq()).isFalse();
        assertThat(response.getContract().getOriginalContractValue()).isEqualByComparingTo("0");
        assertThat(response.getContract().getFinalContractValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("PART_PAID counts toward issued and outstanding, not paid")
    void partPaidCountsAsOutstanding() {
        auth(Role.ADMIN);
        approvedBoq = new BoqDocument();
        approvedBoq.setGrandTotal(new BigDecimal("100000"));
        commercial = new ProjectCommercial();
        commercial.setCurrentContractValue(new BigDecimal("100000"));

        BillingMilestone m = milestone("40000");
        payment(m, BillingStatus.PART_PAID, "40000", 1);

        FinalAccountResponse response = service.get(projectId);

        assertThat(response.getBilling().getTotalIssued()).isEqualByComparingTo("40000");
        assertThat(response.getBilling().getTotalPaid()).isEqualByComparingTo("0");
        assertThat(response.getBilling().getOutstanding()).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("Latest payment request per milestone is used")
    void usesLatestPaymentRequestOnly() {
        auth(Role.ADMIN);
        approvedBoq = new BoqDocument();
        approvedBoq.setGrandTotal(new BigDecimal("100000"));
        commercial = new ProjectCommercial();
        commercial.setCurrentContractValue(new BigDecimal("100000"));

        BillingMilestone m = milestone("50000");
        payment(m, BillingStatus.ISSUED, "50000", 10); // older
        payment(m, BillingStatus.PAID, "50000", 1); // latest

        FinalAccountResponse response = service.get(projectId);

        assertThat(response.getBilling().getTotalIssued()).isEqualByComparingTo("50000");
        assertThat(response.getBilling().getTotalPaid()).isEqualByComparingTo("50000");
        assertThat(response.getBilling().getOutstanding()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Client role is forbidden")
    void clientForbidden() {
        auth(Role.CLIENT);
        assertThatThrownBy(() -> service.get(projectId)).isInstanceOf(ForbiddenException.class);
    }
}
