package com.fitouts.resource.api;

import java.util.List;
import java.util.UUID;

import com.fitouts.resource.domain.ResourceKind;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlantToolUtilisationResponse {
    private Long projectId;
    private long totalResourceDays;
    private long assignmentCount;
    private List<TypeUtilisationItem> resources;

    @Data
    @Builder
    public static class TypeUtilisationItem {
        private UUID resourceTypeUuid;
        private String resourceTypeName;
        private ResourceKind kind;
        private long assignedDays;
        private long quantityDays;
        private long assignmentCount;
    }
}
