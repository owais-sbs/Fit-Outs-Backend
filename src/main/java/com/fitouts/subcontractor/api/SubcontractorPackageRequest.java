package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

import lombok.Data;

@Data
public class SubcontractorPackageRequest {
    private String name;
    private String boqSectionCode;
    private SubcontractorPackageStatus status;
    private String tradePackageCode;
    private List<UUID> boqLineIds;
    private String ldTerms;
    private OffsetDateTime siteVisitAt;
    private OffsetDateTime tenderDeadline;
    private Integer quoteValidityDays;
    private String paymentTerms;
    private BigDecimal retentionPct;
    private String tenderDescription;
    private List<ScFreeIssueMaterialRequest> freeIssueMaterials;
    private List<ScAttendanceRequest> attendanceMatrix;
}
