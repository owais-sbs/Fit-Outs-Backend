package com.fitouts.subcontractor.api;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ScMeasureClaimRequest {
    private BigDecimal measuredQty;
    private BigDecimal measuredValue;
}
