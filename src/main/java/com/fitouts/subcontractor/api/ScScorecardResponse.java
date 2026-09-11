package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScScorecardResponse {
    private UUID uuid;
    private UUID organizationUuid;
    private UUID packageUuid;
    private BigDecimal qualityScore;
    private BigDecimal programmeScore;
    private BigDecimal safetyScore;
    private BigDecimal commercialScore;
    private BigDecimal responsivenessScore;
    private BigDecimal totalScore;
    private OffsetDateTime computedAt;
}
