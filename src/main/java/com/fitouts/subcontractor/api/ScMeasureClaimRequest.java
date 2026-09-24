package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class ScMeasureClaimRequest {
    private BigDecimal measuredQty;
    private BigDecimal measuredValue;
    private List<ScMeasureClaimLineRequest> lines;
}
