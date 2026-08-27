package com.fitouts.planning.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.planning.domain.PlanAreaStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanningStatusResponse {
    private Long projectId;
    private UUID companyId;
    private PlanAreaStatus materialStatus;
    private PlanAreaStatus resourceStatus;
    private PlanAreaStatus labourStatus;
    private PlanAreaStatus subcontractorStatus;
    private boolean planningReady;
    private boolean ganttPublishAllowed;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
}
