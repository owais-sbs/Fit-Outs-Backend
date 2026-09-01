package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProgressUpdateResponse {
    private UUID uuid;
    private UUID activityUuid;
    private int percentComplete;
    private String notes;
    private BigDecimal labourHours;
    private Long reportedBy;
    private OffsetDateTime reportedAt;

    /** PENDING, APPROVED, REJECTED — from linked progress_validation */
    private String validationStatus;
    private String validationReason;
}
