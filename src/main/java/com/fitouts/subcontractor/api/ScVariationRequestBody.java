package com.fitouts.subcontractor.api;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ScVariationRequestBody {
    private String title;
    private String description;
    private BigDecimal qtyDelta;
    private String unit;
    private BigDecimal estimatedCost;
}
