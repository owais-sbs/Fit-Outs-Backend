package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitouts.schedule.domain.ActivityMaterialIssueStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialIssueResponse {
    private UUID uuid;
    private UUID activityUuid;
    private UUID progressUpdateUuid;
    private UUID planLineUuid;
    private UUID materialId;
    private String materialName;
    private BigDecimal qty;
    private BigDecimal plannedQty;
    private ActivityMaterialIssueStatus status;
}
