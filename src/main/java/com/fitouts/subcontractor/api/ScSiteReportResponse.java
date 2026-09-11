package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subcontractor.domain.ScSiteReportStatus;
import com.fitouts.subcontractor.domain.ScSiteReportType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScSiteReportResponse {
    private UUID uuid;
    private UUID packageUuid;
    private Long projectId;
    private UUID companyId;
    private ScSiteReportType reportType;
    private String title;
    private String description;
    private String delayReasonCode;
    private LocalDate expectedDate;
    private LocalDate deliveredDate;
    private String materialName;
    private BigDecimal quantity;
    private String unit;
    private ScSiteReportStatus status;
    private String attachmentPaths;
    private Long raisedBy;
    private OffsetDateTime acknowledgedAt;
    private OffsetDateTime resolvedAt;
    private String resolutionNotes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String packageName;
    private String projectName;
}
