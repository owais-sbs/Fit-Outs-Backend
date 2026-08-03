package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.roomcollab.domain.RoomTaskEventType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomTaskEventResponse {
    private UUID uuid;
    private UUID taskId;
    private RoomTaskEventType eventType;
    private Long actorAccountId;
    private String message;
    private String metadataJson;
    private OffsetDateTime createdAt;
}
