package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;
import com.fitouts.subcontractor.domain.ScFreeIssueMaterial;
import com.fitouts.subcontractor.domain.ScPackageAttendance;

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
    private UUID boqLineId;
    private List<ScBoqLineView> boqLines;
    private BigDecimal estimatedBoqValue;
    private String tradePackageCode;
    private String tradePackageName;
    private String specialistLicenceRequired;
    private String ldTerms;
    private int boqLineCount;
    private String boqLineDescription;
    private String boqLineUnit;
    private String boqRoomLabel;
    private String boqFloorLabel;
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

    private String tenderStatus;
    private OffsetDateTime tenderDeadline;
    private OffsetDateTime tenderIssuedAt;
    private Integer quoteValidityDays;
    private String paymentTerms;
    private java.math.BigDecimal retentionPct;
    private OffsetDateTime siteVisitAt;
    private String tenderDescription;
    private List<ScFreeIssueMaterial> freeIssueMaterials;
    private List<ScPackageAttendance> attendanceMatrix;
}
