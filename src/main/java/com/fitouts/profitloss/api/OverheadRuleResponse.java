package com.fitouts.profitloss.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fitouts.profitloss.domain.OverheadBasis;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OverheadRuleResponse {
    private UUID uuid;
    private BigDecimal percentage;
    private OverheadBasis basis;
    private boolean active;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
