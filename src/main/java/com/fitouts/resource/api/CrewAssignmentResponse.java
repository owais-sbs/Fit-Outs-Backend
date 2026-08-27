package com.fitouts.resource.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CrewAssignmentResponse {
    private UUID uuid;
    private UUID activityUuid;
    private UUID crewUuid;
    private String crewName;
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
}
