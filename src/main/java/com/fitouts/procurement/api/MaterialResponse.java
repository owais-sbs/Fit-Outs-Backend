package com.fitouts.procurement.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.UnitType;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialResponse {
    private UUID id;
    private UUID companyId;
    private UUID materialCategoryId;
    private String materialCategoryName;
    private String materialName;
    private String materialCode;
    private UnitType unitType;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private String supplierName;
    private String sku;
    private BigDecimal minStockLevel;
    private BigDecimal reorderQty;
    private String description;
    private Boolean active;
    private BigDecimal quantityOnHand;
    private Boolean lowStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
