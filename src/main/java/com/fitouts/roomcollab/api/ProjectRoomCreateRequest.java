package com.fitouts.roomcollab.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectRoomCreateRequest {
    @NotBlank
    private String name;
    private String floorLabel;
    private UUID roomTypeId;
    private Integer sortOrder;
}
