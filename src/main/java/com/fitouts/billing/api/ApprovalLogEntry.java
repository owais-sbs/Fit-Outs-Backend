package com.fitouts.billing.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalLogEntry {
    private UUID id;
    private String action;
    private String step;
    private String actorName;
    private String actorRole;
    private String comments;
    private OffsetDateTime createdAt;
}
