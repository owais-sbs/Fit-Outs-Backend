package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitouts.roomcollab.domain.RoomTaskStatus;
import com.fitouts.roomcollab.domain.RoomTaskType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomTaskResponse {
    private UUID uuid;
    private UUID projectRoomId;
    private Long projectId;
    private String roomName;
    private String floorLabel;
    private String title;
    private RoomTaskType taskType;
    private String typeLabel;
    private RoomTaskStatus status;
    private OffsetDateTime clientDeadline;
    private Long createdBy;
    private Long assigneeAccountId;
    private OffsetDateTime firstSentToClientAt;
    private OffsetDateTime approvedAt;
    private Integer clientApprovalDays;
    private Integer revisionCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Builder.Default
    private List<RoomTaskFileVersionResponse> versions = new ArrayList<>();
}
