package com.fitouts.planning.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanningGateConfigResponse {
    private UUID companyId;
    private boolean requireMaterial;
    private boolean requireResource;
    private boolean requireLabour;
    private boolean requireSubcontractor;
    private boolean requirePlanningReady;
    private OffsetDateTime updatedAt;
}
