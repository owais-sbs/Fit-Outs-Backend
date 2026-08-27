package com.fitouts.communications.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ChannelMessageResponse {

    private UUID uuid;
    private UUID channelUuid;
    private Long senderAccountId;
    private String senderName;
    private String body;
    private String attachmentUrl;
    private String attachmentName;
    private OffsetDateTime createdAt;
}
