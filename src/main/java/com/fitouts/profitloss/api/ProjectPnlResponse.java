package com.fitouts.profitloss.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectPnlResponse {
    private UUID uuid;
    private Long projectId;
    private String projectName;
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
    private BigDecimal originalContractValue;
    private BigDecimal originalEstimatedCost;
    private BigDecimal marginVsOriginalEstimate;
    private OffsetDateTime calculatedAt;
}
