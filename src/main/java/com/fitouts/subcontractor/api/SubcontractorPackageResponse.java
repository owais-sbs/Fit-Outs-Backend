package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubcontractorPackageResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private String name;
    private String boqSectionCode;
    private SubcontractorPackageStatus status;
    private Long appointedAccountId;
    private String appointedCompanyName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Sum of BOQ line quantities for this package section (when BOQ approved). */
    private java.math.BigDecimal boqPlannedQty;
    /** Sum of approved claim quantities on this package. */
    private java.math.BigDecimal approvedClaimedQty;
    /** boqPlannedQty - approvedClaimedQty (min 0). */
    private java.math.BigDecimal remainingQty;

    private String projectName;
    private String projectLocation;
    private String projectStatus;
    private String projectType;
    private String assignedManager;
}
