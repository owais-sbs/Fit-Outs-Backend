package com.fitouts.planning.api;

import com.fitouts.planning.domain.PlanAreaStatus;

import lombok.Data;

@Data
public class PlanningStatusRequest {
    private PlanAreaStatus materialStatus;
    private PlanAreaStatus resourceStatus;
    private PlanAreaStatus labourStatus;
    private PlanAreaStatus subcontractorStatus;
    private Boolean planningReady;
    private Boolean ganttPublishAllowed;
}
