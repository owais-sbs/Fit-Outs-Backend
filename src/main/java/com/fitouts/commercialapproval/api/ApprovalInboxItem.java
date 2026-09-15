package com.fitouts.commercialapproval.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.auth.domain.Role;
import com.fitouts.commercialapproval.domain.CommercialApprovalTaskStatus;
import com.fitouts.commercialapproval.domain.CommercialEventType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalInboxItem {
    private UUID taskUuid;
    private UUID runUuid;
    private CommercialEventType eventType;
    private UUID entityUuid;
    private Long projectId;
    private BigDecimal amount;
    private Role role;
    private CommercialApprovalTaskStatus status;
    private OffsetDateTime dueAt;
    private String title;
}
