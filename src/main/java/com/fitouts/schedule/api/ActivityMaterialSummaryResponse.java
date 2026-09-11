package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityMaterialSummaryResponse {
    private UUID materialId;
    private String materialName;
    private UUID planLineUuid;
    private BigDecimal plannedQty;
    private BigDecimal issuedQty;
    private BigDecimal declaredQty;
    private BigDecimal remainingQty;
    private String unit;
}
