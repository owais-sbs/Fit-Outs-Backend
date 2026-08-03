package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomMessageResponse {
    private UUID uuid;
    private UUID projectRoomId;
    private UUID taskId;
    private Long senderAccountId;
    private String senderName;
    private String body;
    private String attachmentName;
    private String attachmentUrl;
    private UUID linkedTaskId;
    private UUID referencedVersionId;
    private Integer referencedVersionNo;
    private String referencedFileName;
    private String referencedDownloadUrl;
    private OffsetDateTime createdAt;
}
