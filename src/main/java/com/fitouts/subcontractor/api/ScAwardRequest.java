package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class ScAwardRequest {
    private UUID organizationUuid;
    private UUID quoteUuid;
    private BigDecimal awardedValue;
}
