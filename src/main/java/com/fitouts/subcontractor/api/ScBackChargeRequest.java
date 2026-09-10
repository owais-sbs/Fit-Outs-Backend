package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class ScBackChargeRequest {
    private UUID packageUuid;
    private Long projectId;
    private UUID organizationUuid;
    private String chargeType;
    private String description;
    private BigDecimal amount;
    private String evidencePaths;
}
