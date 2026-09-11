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
    private BigDecimal maxPackageValue;
}
