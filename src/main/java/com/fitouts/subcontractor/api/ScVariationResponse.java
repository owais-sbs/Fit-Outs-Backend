package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subcontractor.domain.ScVariationStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScVariationResponse {
    private UUID uuid;
    private UUID packageUuid;
    private Long projectId;
    private UUID companyId;
    private String title;
    private String description;
    private BigDecimal qtyDelta;
    private String unit;
    private BigDecimal estimatedCost;
    private ScVariationStatus status;
    private String attachmentPaths;
    private Long submittedBy;
    private OffsetDateTime submittedAt;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String reason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String packageName;
    private String projectName;
}
