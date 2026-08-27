package com.fitouts.planning.api;

import lombok.Data;

@Data
public class PlanningGateConfigRequest {
    private Boolean requireMaterial;
    private Boolean requireResource;
    private Boolean requireLabour;
    private Boolean requireSubcontractor;
    private Boolean requirePlanningReady;
}
