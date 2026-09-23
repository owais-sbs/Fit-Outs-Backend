package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScComparisonLineCell {
    private UUID boqLineId;
    private String description;
    private String sectionCode;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal rate;
    private BigDecimal amount;
    private String lineStatus;
    private String remarks;
    private boolean cheapest;
}
