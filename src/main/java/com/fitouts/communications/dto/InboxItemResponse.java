package com.fitouts.communications.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class InboxItemResponse {

    private UUID channelUuid;
    private String channelType;
    private String name;
    private String lastMessage;
    private OffsetDateTime lastMessageAt;
    private Integer unreadCount;
    private Long projectId;
    private UUID projectRoomId;
    private UUID roomTaskId;
    private String contextLabel;
}
