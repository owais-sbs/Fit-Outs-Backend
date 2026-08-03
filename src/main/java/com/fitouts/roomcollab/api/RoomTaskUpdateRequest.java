package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomTaskUpdateRequest {
    private String title;
    private OffsetDateTime clientDeadline;
    private Long assigneeAccountId;
}
