package com.fitouts.resource.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class CrewAssignmentRequest {
    private UUID activityUuid;
    private UUID crewUuid;
    private LocalDate startDate;
    private LocalDate endDate;
}
