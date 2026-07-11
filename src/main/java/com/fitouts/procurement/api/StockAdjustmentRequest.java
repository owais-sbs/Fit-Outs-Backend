package com.fitouts.procurement.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentRequest {
    private UUID materialId;
    private BigDecimal quantity;
    private String notes;
}
