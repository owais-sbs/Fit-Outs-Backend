package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScComparisonResponse {
    private UUID packageUuid;
    private boolean deadlinePassed;
    private List<ScComparisonBidderRow> bidders;
}
