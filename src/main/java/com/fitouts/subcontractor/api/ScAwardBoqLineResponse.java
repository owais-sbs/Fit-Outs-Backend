package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScAwardBoqLineResponse {
    private UUID uuid;
    private UUID boqLineId;
    private String sectionCode;
    private String description;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal rate;
    private BigDecimal amount;
    private String lineStatus;
    private String remarks;
}
