package com.fitouts.roomconfiguration.api;

import java.util.UUID;
import com.fitouts.shared.enums.RoomCategory;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeFilterRequest {

    private String search;
    private RoomCategory category;
    private UUID roomMasterId;
    private Boolean active;
}
