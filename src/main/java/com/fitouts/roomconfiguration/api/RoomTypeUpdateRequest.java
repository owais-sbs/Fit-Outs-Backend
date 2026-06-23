package com.fitouts.roomconfiguration.api;

import java.util.List;
import java.util.UUID;

import com.fitouts.shared.enums.RoomCategory;

// import jakarta.validation.constraints.NotNull;
// import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeUpdateRequest {

    // @Size(max = 200, message = "Room type name must not exceed 200 characters")
    private String roomTypeName;

    // @Size(max = 50, message = "Room code must not exceed 50 characters")
    private String roomCode;

    private RoomCategory category;

    private UUID roomMasterId;

    // @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private List<UUID> workItemIds;
}
