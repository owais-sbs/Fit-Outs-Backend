package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;
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
    private String exclusionsText;
    private String qualificationsText;
    private String paymentTerms;
    private BigDecimal retentionPct;
    private BigDecimal preliminariesValue;
    private BigDecimal provisionalSumsValue;
    private int quotedLineCount;
    private int excludedLineCount;
    private int alternativeLineCount;
    private List<String> scopeGaps;
    private List<ScComparisonLineCell> lines;
    private boolean cheapestTotal;
}
