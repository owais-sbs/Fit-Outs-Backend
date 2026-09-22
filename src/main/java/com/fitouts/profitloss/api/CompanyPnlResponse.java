package com.fitouts.profitloss.api;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyPnlResponse {
    private String periodYearMonth;
    private BigDecimal contractValue;
    private BigDecimal materialCost;
    private BigDecimal labourCost;
    private BigDecimal scCertifiedCost;
    private BigDecimal variationCost;
    private BigDecimal overheadAllocated;
    private BigDecimal totalCost;
    private BigDecimal margin;
    private BigDecimal marginPercent;
    private List<ProjectPnlResponse> projects;
}
