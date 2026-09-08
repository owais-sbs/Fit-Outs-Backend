package com.fitouts.notification.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationResponse {

    private UUID uuid;
    private String category;
    private String severity;
    private String title;
    private String body;
    private String linkPath;
    private String sourceType;
    private UUID sourceUuid;
    private boolean read;
    private OffsetDateTime createdAt;
}
