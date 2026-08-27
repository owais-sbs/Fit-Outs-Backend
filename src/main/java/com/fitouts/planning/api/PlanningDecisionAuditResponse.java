package com.fitouts.planning.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanningDecisionAuditResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private String decisionType;
    private String fromValue;
    private String toValue;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String notes;
}
