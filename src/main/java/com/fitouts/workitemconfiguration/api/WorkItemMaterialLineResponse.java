package com.fitouts.workitemconfiguration.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitouts.shared.enums.UnitType;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemMaterialLineResponse {
    private UUID materialId;
    private String materialName;
    private String materialCode;
    private String materialCategoryName;
    private UnitType unitType;
    private BigDecimal costPrice;
    private BigDecimal quantityPerUnit;
    private BigDecimal wastagePercent;
    private BigDecimal lineCost;
}
