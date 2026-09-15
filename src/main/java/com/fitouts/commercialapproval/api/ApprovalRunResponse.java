package com.fitouts.commercialapproval.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.auth.domain.Role;
import com.fitouts.commercialapproval.domain.CommercialApprovalRunStatus;
import com.fitouts.commercialapproval.domain.CommercialApprovalTaskStatus;
import com.fitouts.commercialapproval.domain.CommercialEventType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalRunResponse {
    private UUID uuid;
    private CommercialEventType eventType;
    private UUID entityUuid;
    private Long projectId;
    private BigDecimal amount;
    private CommercialApprovalRunStatus status;
    private int currentStepOrder;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private List<TaskResponse> tasks;

    @Data
    @Builder
    public static class TaskResponse {
        private UUID uuid;
        private int stepOrder;
        private Role role;
        private CommercialApprovalTaskStatus status;
        private OffsetDateTime dueAt;
        private Long decidedBy;
        private OffsetDateTime decidedAt;
        private String comment;
    }
}
