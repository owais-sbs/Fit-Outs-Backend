package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class ScQuoteLineRequest {
    private UUID boqLineId;
    private BigDecimal rate;
    private BigDecimal quantity;
    private String lineStatus;
    private String remarks;
}
