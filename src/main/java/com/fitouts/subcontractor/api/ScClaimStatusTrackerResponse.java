package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScClaimStatusTrackerResponse {
    private UUID claimUuid;
    private UUID packageUuid;
    private String packageName;
    private Long projectId;
    private String status;
    private BigDecimal claimedQty;
    private BigDecimal measuredQty;
    private BigDecimal measuredValue;
    private BigDecimal certifiedValue;
    private UUID certificateUuid;
    private OffsetDateTime submittedAt;
    private OffsetDateTime measuredAt;
}
