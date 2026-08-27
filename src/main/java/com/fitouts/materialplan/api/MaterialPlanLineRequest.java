package com.fitouts.materialplan.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class MaterialPlanLineRequest {
    private UUID materialId;
    private String materialName;
    private BigDecimal plannedQty;
    private BigDecimal stockQtySnapshot;
    private String unit;
    private Boolean shortageFlag;
    private BigDecimal reservedQty;
    private String notes;
    private String substituteReason;
    private Integer sortOrder;
}
