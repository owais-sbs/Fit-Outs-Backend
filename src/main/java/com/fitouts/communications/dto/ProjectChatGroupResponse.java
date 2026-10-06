package com.fitouts.communications.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectChatGroupResponse {

    private UUID channelUuid;
    private String name;
    private boolean direct;
    private boolean includesClient;
    private boolean canSend;
    private int memberCount;
    private String lastMessage;
    private OffsetDateTime lastMessageAt;
    private int unreadCount;
    private List<ProjectChatMemberResponse> members;
}
