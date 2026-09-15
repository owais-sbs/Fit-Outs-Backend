package com.fitouts.variation.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectCommercialResponse {
    private UUID uuid;
    private Long projectId;
    private BigDecimal originalContractValue;
    private BigDecimal currentContractValue;
    private BigDecimal originalCost;
    private BigDecimal currentCost;
    private BigDecimal currentMargin;
}
