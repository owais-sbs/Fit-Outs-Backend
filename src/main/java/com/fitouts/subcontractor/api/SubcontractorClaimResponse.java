package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.subcontractor.domain.SubcontractorClaimStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubcontractorClaimResponse {
    private UUID uuid;
    private UUID packageUuid;
    private Long projectId;
    private UUID companyId;
    private BigDecimal claimedQty;
    private BigDecimal plannedQty;
    private BigDecimal claimedValue;
    private String claimNumber;
    private LocalDate claimPeriodFrom;
    private LocalDate claimPeriodTo;
    private String notes;
    private SubcontractorClaimStatus status;
    private Long submittedBy;
    private OffsetDateTime submittedAt;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String reason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Comma-separated stored file paths */
    private String attachmentPaths;

    /** Wave 7 measured/certified fields */
    private java.math.BigDecimal measuredQty;
    private java.math.BigDecimal measuredValue;
    private java.math.BigDecimal certifiedValue;
    private Long measuredBy;
    private OffsetDateTime measuredAt;
    private UUID certificateUuid;

    /** Enriched display fields */
    private BigDecimal measuredQty;
    private BigDecimal measuredValue;
    private BigDecimal certifiedValue;
    /** Downstream payment status from linked certificate (not a primary claim status). */
    private String paymentStatus;
    private List<ScClaimLineResponse> lines;
    private String packageName;
    private String projectName;
    private String subcontractorName;
    private String submittedByName;
    private BigDecimal originalAwardValue;
    private BigDecimal previousCertifiedAmount;
    private BigDecimal remainingContractValue;
}
