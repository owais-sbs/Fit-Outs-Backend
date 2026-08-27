package com.fitouts.procurement.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockBalanceResponse {
    private UUID materialId;
    private String materialName;
    private String materialCode;
    private String materialCategoryName;
    private BigDecimal quantityOnHand;
    private BigDecimal quantityReserved;
    private BigDecimal quantityAvailable;
    private BigDecimal costPrice;
    private BigDecimal stockValue;
    private BigDecimal minStockLevel;
    private Boolean lowStock;
    private LocalDateTime lastUpdated;
}
