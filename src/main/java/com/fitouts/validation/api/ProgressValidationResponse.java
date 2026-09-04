package com.fitouts.validation.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.validation.domain.ProgressValidationStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProgressValidationResponse {
    private UUID uuid;
    private UUID progressUpdateUuid;
    private UUID activityUuid;
    private Long projectId;
    private ProgressValidationStatus status;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String reason;
    private OffsetDateTime createdAt;

    /** Enriched display fields */
    private String projectName;
    private String activityName;
    private Integer percentComplete;
    private String progressNotes;
    private String reportedByName;
    private OffsetDateTime reportedAt;
    private String photoPaths;
}
