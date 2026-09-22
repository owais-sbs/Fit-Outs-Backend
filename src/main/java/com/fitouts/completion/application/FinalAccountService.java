package com.fitouts.completion.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.fitouts.completion.api.FinalAccountResponse.BillingSection;
import com.fitouts.completion.api.FinalAccountResponse.CloseOutSection;
import com.fitouts.completion.api.FinalAccountResponse.ContractSection;
import com.fitouts.completion.api.FinalAccountResponse.VariationLine;
import com.fitouts.completion.api.FinalAccountResponse.VariationsSection;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.variation.domain.ProjectCommercial;
import com.fitouts.variation.domain.ProjectCommercialRepository;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;
import com.fitouts.variation.domain.VariationStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinalAccountService {

    private static final Set<BillingStatus> ISSUED_OR_BEYOND = EnumSet.of(
            BillingStatus.ISSUED,
            BillingStatus.CLIENT_ACCEPTED,
            BillingStatus.PAID,
            BillingStatus.PART_PAID);

    private static final Set<BillingStatus> OUTSTANDING_STATUSES = EnumSet.of(
            BillingStatus.ISSUED,
            BillingStatus.CLIENT_ACCEPTED,
            BillingStatus.PART_PAID);

    private final ProjectService projectService;
    private final BoqProjectRules boqProjectRules;
    private final ProjectCommercialRepository commercialRepository;
    private final VariationRequestRepository variationRepository;
    private final BillingMilestoneRepository milestoneRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final CloseoutChecklistService closeoutChecklistService;

    @Transactional(readOnly = true)
    public FinalAccountResponse get(Long projectId) {
        requireStaff();
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();

        CloseoutChecklistResponse checklist = closeoutChecklistService.get(projectId);

        List<VariationRequest> approvedVariations = variationRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId)
                .stream()
                .filter(v -> v.getStatus() == VariationStatus.APPROVED)
                .sorted(Comparator.comparing(VariationRequest::getCrNumber, Comparator.nullsLast(String::compareTo)))
                .toList();

        BigDecimal approvedVariationsTotal = approvedVariations.stream()
                .map(v -> nullSafe(v.getSellDelta()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BoqDocument approvedBoq = boqProjectRules.findApproved(project.getId()).orElse(null);
        boolean hasApprovedBoq = approvedBoq != null && approvedBoq.getGrandTotal() != null;
        BigDecimal originalContract = hasApprovedBoq ? approvedBoq.getGrandTotal() : BigDecimal.ZERO;

        ProjectCommercial commercial = commercialRepository
                .findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElse(null);
        boolean usingProjectCommercial = commercial != null && commercial.getCurrentContractValue() != null;
        BigDecimal finalContract = usingProjectCommercial
                ? commercial.getCurrentContractValue()
                : originalContract.add(approvedVariationsTotal);

        Item variationsItem = findItem(checklist, CloseoutChecklistService.KEY_VARIATIONS);
        Item finalInvoiceItem = findItem(checklist, CloseoutChecklistService.KEY_FINAL_INVOICE);
        Item accountingItem = findItem(checklist, CloseoutChecklistService.KEY_ACCOUNTING);

        BillingSection billing = buildBilling(project.getId(), companyId, finalContract);

        List<VariationLine> lines = approvedVariations.stream()
                .map(v -> VariationLine.builder()
                        .uuid(v.getUuid())
                        .crNumber(v.getCrNumber())
                        .title(v.getTitle())
                        .sellDelta(nullSafe(v.getSellDelta()))
                        .build())
                .toList();

        return FinalAccountResponse.builder()
                .projectId(project.getId())
                .contract(ContractSection.builder()
                        .originalContractValue(originalContract)
                        .approvedVariationsTotal(approvedVariationsTotal)
                        .finalContractValue(finalContract)
                        .hasApprovedBoq(hasApprovedBoq)
                        .usingProjectCommercial(usingProjectCommercial)
                        .build())
                .variations(VariationsSection.builder()
                        .approvedTotal(approvedVariationsTotal)
                        .closeoutSatisfied(variationsItem != null && variationsItem.isSatisfied())
                        .closeoutDetail(variationsItem != null ? variationsItem.getDetail() : null)
                        .lines(lines)
                        .build())
                .billing(billing)
                .closeOut(CloseOutSection.builder()
                        .finalInvoiceConfirmed(finalInvoiceItem != null && finalInvoiceItem.isSatisfied())
                        .finalInvoiceConfirmedAt(finalInvoiceItem != null ? finalInvoiceItem.getConfirmedAt() : null)
                        .finalInvoiceConfirmedBy(finalInvoiceItem != null ? finalInvoiceItem.getConfirmedBy() : null)
                        .accountingSynced(accountingItem != null && accountingItem.isSatisfied())
                        .accountingSyncedAt(accountingItem != null ? accountingItem.getConfirmedAt() : null)
                        .accountingSyncedBy(accountingItem != null ? accountingItem.getConfirmedBy() : null)
                        .build())
                .checklistAllSatisfied(checklist.isAllSatisfied())
                .commercialStage(checklist.getCommercialStage())
                .archiveEligible(checklist.isArchiveEligible())
                .dlpStartDate(checklist.getDlpStartDate())
                .dlpEndDate(checklist.getDlpEndDate())
                .commerciallyClosedAt(checklist.getCommerciallyClosedAt())
                .archivedAt(checklist.getArchivedAt())
                .build();
    }

    private BillingSection buildBilling(Long projectId, UUID companyId, BigDecimal finalContract) {
        List<BillingMilestone> milestones = milestoneRepository
                .findByProjectIdAndCompanyIdOrderByDueDateAscCreatedAtAsc(projectId, companyId);

        BigDecimal totalScheduled = milestones.stream()
                .map(m -> nullSafe(m.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIssued = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;

        for (BillingMilestone milestone : milestones) {
            PaymentRequest latest = paymentRequestRepository
                    .findByMilestoneUuidAndCompanyIdOrderByCreatedAtDesc(milestone.getUuid(), companyId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (latest == null || latest.getStatus() == null) {
                continue;
            }
            BillingStatus status = latest.getStatus();
            BigDecimal amount = nullSafe(latest.getAmount());
            if (ISSUED_OR_BEYOND.contains(status)) {
                totalIssued = totalIssued.add(amount);
            }
            if (status == BillingStatus.PAID) {
                totalPaid = totalPaid.add(amount);
            }
            if (OUTSTANDING_STATUSES.contains(status)) {
                outstanding = outstanding.add(amount);
            }
        }

        BigDecimal unbilled = finalContract.subtract(totalIssued);
        if (unbilled.signum() < 0) {
            unbilled = BigDecimal.ZERO;
        }

        return BillingSection.builder()
                .totalScheduled(totalScheduled)
                .totalIssued(totalIssued)
                .totalPaid(totalPaid)
                .outstanding(outstanding)
                .unbilledRemaining(unbilled)
                .build();
    }

    private static Item findItem(CloseoutChecklistResponse checklist, String key) {
        if (checklist == null || checklist.getItems() == null) {
            return null;
        }
        return checklist.getItems().stream()
                .filter(i -> key.equals(i.getKey()))
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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
