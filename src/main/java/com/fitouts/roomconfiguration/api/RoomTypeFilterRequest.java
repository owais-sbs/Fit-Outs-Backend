package com.fitouts.roomconfiguration.api;

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
    private Boolean active;
    private Boolean ceilingMeasurementRequired;
    private Boolean wallMeasurementRequired;
    private Boolean floorMeasurementRequired;
}
