package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScPrequalificationReviewRequest {

    private String status;
    private String notes;
    private List<String> approvedTrades;
    /** Per-trade Approve/Reject with mandatory reason on reject. */
    private List<ScTradeDecisionRequest> tradeDecisions;
    private BigDecimal maxPackageValue;
}
