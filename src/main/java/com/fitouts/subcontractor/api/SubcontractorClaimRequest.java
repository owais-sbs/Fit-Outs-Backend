package com.fitouts.subcontractor.api;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class SubcontractorClaimRequest {
    private BigDecimal claimedQty;
    private BigDecimal plannedQty;
    private String notes;
}
