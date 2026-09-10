package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScQuoteLineResponse {
    private UUID uuid;
    private UUID boqLineId;
    private BigDecimal rate;
    private BigDecimal quantity;
    private BigDecimal amount;
    private String lineStatus;
    private String remarks;
}
