package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScPackageWorkerResponse {
    private UUID uuid;
    private UUID packageUuid;
    private UUID workerUuid;
    private String workerName;
    private String trade;
    private String status;
    private boolean siteEligible;
    private String siteEligibilityNote;
    private String notes;
    private OffsetDateTime nominatedAt;
}
