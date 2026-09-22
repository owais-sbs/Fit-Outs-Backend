package com.fitouts.completion.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.completion.domain.CommercialLifecycleStage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinalAccountResponse {

    private Long projectId;
    private ContractSection contract;
    private VariationsSection variations;
    private BillingSection billing;
    private CloseOutSection closeOut;
    private boolean checklistAllSatisfied;
    private CommercialLifecycleStage commercialStage;
    private boolean archiveEligible;
    private LocalDate dlpStartDate;
    private LocalDate dlpEndDate;
    private OffsetDateTime commerciallyClosedAt;
    private OffsetDateTime archivedAt;

    @Data
    @Builder
    public static class ContractSection {
        private BigDecimal originalContractValue;
        private BigDecimal approvedVariationsTotal;
        private BigDecimal finalContractValue;
        private boolean hasApprovedBoq;
        private boolean usingProjectCommercial;
    }

    @Data
    @Builder
    public static class VariationsSection {
        private BigDecimal approvedTotal;
        private boolean closeoutSatisfied;
        private String closeoutDetail;
        private List<VariationLine> lines;
    }

    @Data
    @Builder
    public static class VariationLine {
        private UUID uuid;
        private String crNumber;
        private String title;
        private BigDecimal sellDelta;
    }

    @Data
    @Builder
    public static class BillingSection {
        private BigDecimal totalScheduled;
        private BigDecimal totalIssued;
        private BigDecimal totalPaid;
        private BigDecimal outstanding;
        private BigDecimal unbilledRemaining;
    }

    @Data
    @Builder
    public static class CloseOutSection {
        private boolean finalInvoiceConfirmed;
        private OffsetDateTime finalInvoiceConfirmedAt;
        private Long finalInvoiceConfirmedBy;
        private boolean accountingSynced;
        private OffsetDateTime accountingSyncedAt;
        private Long accountingSyncedBy;
    }
}
