package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    /** Enriched display fields */
    private String packageName;
    private String projectName;
    private String subcontractorName;
    private String submittedByName;
}
