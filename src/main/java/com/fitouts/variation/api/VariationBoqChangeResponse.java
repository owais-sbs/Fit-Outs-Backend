package com.fitouts.variation.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.variation.domain.VariationLineType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariationBoqChangeResponse {
    private UUID uuid;
    private UUID variationLineUuid;
    private UUID sourceBoqUuid;
    private UUID resultingBoqUuid;
    private UUID sourceBoqLineId;
    private UUID resultingBoqLineId;
    private VariationLineType changeType;
    private String description;
    private String unit;
    private BigDecimal previousQuantity;
    private BigDecimal newQuantity;
    private BigDecimal previousRate;
    private BigDecimal newRate;
    private BigDecimal previousAmount;
    private BigDecimal newAmount;
    private BigDecimal deltaAmount;
    private Long appliedBy;
    private OffsetDateTime createdAt;
}
