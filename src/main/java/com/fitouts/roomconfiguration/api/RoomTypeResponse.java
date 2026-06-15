package com.fitouts.roomconfiguration.api;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.fitouts.shared.enums.RoomCategory;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeResponse {

    private UUID id;
    private UUID companyId;
    private String roomTypeName;
    private String roomCode;
    private RoomCategory category;
    private String description;
    private Boolean ceilingMeasurementRequired;
    private Boolean wallMeasurementRequired;
    private Boolean floorMeasurementRequired;
    private Boolean active;
    private Set<WorkItemSummaryResponse> workItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkItemSummaryResponse {
        private UUID id;
        private String workItemName;
        private String workItemCode;
        private String icon;
        private String colorTag;
    }
}
