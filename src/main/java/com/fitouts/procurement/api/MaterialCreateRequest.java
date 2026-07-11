package com.fitouts.procurement.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitouts.shared.enums.UnitType;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialCreateRequest {
    private String materialName;
    private String materialCode;
    private UUID materialCategoryId;
    private UnitType unitType;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private String supplierName;
    private String sku;
    private BigDecimal minStockLevel;
    private BigDecimal reorderQty;
    private String description;
}
