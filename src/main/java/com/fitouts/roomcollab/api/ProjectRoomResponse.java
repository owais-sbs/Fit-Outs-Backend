package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.roomcollab.domain.RoomSource;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectRoomResponse {
    private UUID uuid;
    private Long projectId;
    private String name;
    private String floorLabel;
    private UUID roomTypeId;
    private RoomSource source;
    private Integer sortOrder;
    private Integer taskCount;
    private Integer openTaskCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
