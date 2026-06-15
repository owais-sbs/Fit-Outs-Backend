package com.fitouts.roomconfiguration.api;

import java.util.Set;
import java.util.UUID;

import com.fitouts.shared.enums.RoomCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeCreateRequest {

    @NotBlank(message = "Room type name is required")
    @Size(max = 200, message = "Room type name must not exceed 200 characters")
    private String roomTypeName;

    @NotBlank(message = "Room code is required")
    @Size(max = 50, message = "Room code must not exceed 50 characters")
    private String roomCode;

    @NotNull(message = "Category is required")
    private RoomCategory category;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Builder.Default
    private Boolean ceilingMeasurementRequired = false;

    @Builder.Default
    private Boolean wallMeasurementRequired = false;

    @Builder.Default
    private Boolean floorMeasurementRequired = false;

    private Set<UUID> workItemIds;
}
