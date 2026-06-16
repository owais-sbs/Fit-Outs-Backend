package com.fitouts.roomconfiguration.api;

import java.time.LocalDateTime;
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
    private UUID roomMasterId;
    private String roomMasterName;
    private String description;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
}
