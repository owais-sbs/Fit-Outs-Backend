package com.fitouts.schedule.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.schedule.domain.DelayReasonCode;
import com.fitouts.schedule.domain.ScheduleDurationExtensionStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DurationExtensionResponse {
    private UUID uuid;
    private UUID companyId;
    private Long projectId;
    private UUID activityUuid;
    private Long requestedBy;
    private Integer currentDurationWorkingDays;
    private Integer requestedDurationWorkingDays;
    private DelayReasonCode delayReasonCode;
    private String delayReasonText;
    private ScheduleDurationExtensionStatus status;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private String decisionNotes;
    private OffsetDateTime createdAt;

    /** Enriched display fields */
    private String projectName;
    private String activityName;
    private String requestedByName;
    private String delayReasonLabel;
}
