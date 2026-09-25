package com.fitouts.resource.api;

import java.time.LocalDate;
import java.util.UUID;

import com.fitouts.resource.domain.ResourceKind;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceAssignmentResponse {
    private UUID uuid;
    private UUID activityUuid;
    private UUID resourceTypeUuid;
    private String resourceTypeName;
    private ResourceKind kind;
    private int quantity;
    private Long projectId;
    private LocalDate startDate;
    private LocalDate endDate;
}
