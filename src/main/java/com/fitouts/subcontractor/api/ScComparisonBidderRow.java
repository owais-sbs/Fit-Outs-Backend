package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScComparisonBidderRow {
    private UUID organizationUuid;
    private String organizationName;
    private String bidderStatus;
    private UUID quoteUuid;
    private BigDecimal totalValue;
    private Integer leadTimeDays;
}
