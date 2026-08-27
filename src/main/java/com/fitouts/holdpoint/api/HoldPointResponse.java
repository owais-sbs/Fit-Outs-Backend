package com.fitouts.holdpoint.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.holdpoint.domain.HoldPointStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HoldPointResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private UUID activityUuid;
    private String title;
    private HoldPointStatus status;
    private String checklistJson;
    private List<String> checklistItems;
    private String activityType;
    private String notes;
    private Long createdBy;
    private Long decidedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
