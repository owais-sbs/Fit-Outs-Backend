package com.fitouts.subcontractor.api;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ScCertifyClaimRequest {
    private BigDecimal certifiedValue;
    private String accountingRef;
}
