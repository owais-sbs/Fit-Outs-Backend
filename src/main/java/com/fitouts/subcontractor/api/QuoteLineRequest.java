package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteLineRequest {

    private UUID boqLineId;
    private String sectionCode;
    private String description;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitRate;
    private String remarks;
}
