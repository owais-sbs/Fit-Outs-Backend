package com.fitouts.resource.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class ResourceAssignmentRequest {
    private UUID activityUuid;
    private UUID resourceTypeUuid;
    private Integer quantity;
    private LocalDate startDate;
    private LocalDate endDate;
}
