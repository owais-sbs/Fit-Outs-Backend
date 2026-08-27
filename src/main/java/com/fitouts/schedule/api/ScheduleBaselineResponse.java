package com.fitouts.schedule.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleBaselineResponse {
    private UUID uuid;
    private String name;
    private OffsetDateTime createdAt;
    private Long createdBy;
}
