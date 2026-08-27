package com.fitouts.resource.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceUtilisationResponse {
    private Long projectId;
    private long totalCrewDays;
    private long assignmentCount;
    private List<CrewUtilisationItem> crews;

    @Data
    @Builder
    public static class CrewUtilisationItem {
        private UUID crewUuid;
        private String crewName;
        private int headcount;
        private long assignedDays;
        private long assignmentCount;
    }
}
