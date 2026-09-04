package com.fitouts.snag.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.snag.domain.SnagSeverity;
import com.fitouts.snag.domain.SnagStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SnagResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private String title;
    private String description;
    private String location;
    private UUID projectRoomId;
    private String roomName;
    private UUID activityUuid;
    private String activityName;
    private String photoPaths;
    private SnagStatus status;
    private SnagSeverity severity;
    private LocalDate dueDate;
    private Long raisedBy;
    private String raisedByName;
    private boolean raisedByClient;
    private Long assigneeAccountId;
    private String assigneeName;
    private boolean clientVisible;
    private OffsetDateTime clientApprovedAt;
    private Long clientApprovedBy;
    private String clientApprovedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
