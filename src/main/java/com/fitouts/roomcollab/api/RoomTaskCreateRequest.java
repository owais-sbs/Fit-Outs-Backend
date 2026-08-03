package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.roomcollab.domain.RoomTaskStatus;
import com.fitouts.roomcollab.domain.RoomTaskType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomTaskCreateRequest {
    private UUID projectRoomId;
    private String title;
    private RoomTaskType taskType = RoomTaskType.OTHER;
    /** Custom label when taskType is CUSTOM, or optional display override. */
    private String typeLabel;
    private OffsetDateTime clientDeadline;
    private Long assigneeAccountId;
}
