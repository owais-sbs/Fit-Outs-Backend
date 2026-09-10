package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScBackChargeResponse {
    private UUID uuid;
    private UUID packageUuid;
    private Long projectId;
    private UUID organizationUuid;
    private String chargeType;
    private String description;
    private BigDecimal amount;
    private String evidencePaths;
    private String status;
    private boolean disputed;
    private OffsetDateTime acknowledgedAt;
    private OffsetDateTime createdAt;
}
