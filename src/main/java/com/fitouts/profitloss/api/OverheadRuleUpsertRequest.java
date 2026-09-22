package com.fitouts.profitloss.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class OverheadRuleUpsertRequest {
    private BigDecimal percentage;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
