package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class SubcontractorClaimRequest {
    /** Legacy package-level claimed quantity (used when lines are empty). */
    private BigDecimal claimedQty;
    private BigDecimal plannedQty;
    private String notes;
    private LocalDate claimPeriodFrom;
    private LocalDate claimPeriodTo;
    private List<ScClaimLineRequest> lines = new ArrayList<>();
}
