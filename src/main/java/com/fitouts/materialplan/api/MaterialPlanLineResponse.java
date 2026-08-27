package com.fitouts.materialplan.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialPlanLineResponse {
    private UUID uuid;
    private UUID materialId;
    private String materialName;
    private BigDecimal plannedQty;
    private BigDecimal stockQtySnapshot;
    private String unit;
    private boolean shortageFlag;
    private BigDecimal reservedQty;
    private String notes;
    private String substituteReason;
    private int sortOrder;
}
