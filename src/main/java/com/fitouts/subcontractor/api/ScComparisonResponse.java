package com.fitouts.subcontractor.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScComparisonResponse {
    private UUID packageUuid;
    private String packageName;
    private boolean deadlinePassed;
    private boolean sealed;
    private String paymentTerms;
    private java.math.BigDecimal retentionPct;
    private List<ScComparisonScopeLine> scopeLines;
    private List<ScComparisonBidderRow> bidders;
}
