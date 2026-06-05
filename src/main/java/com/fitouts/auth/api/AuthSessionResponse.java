package com.fitouts.auth.api;

import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthSessionResponse {

    private String sessionId;
    private Long deviceId;
    private String deviceLabel;
    private String userAgent;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime trustedUntil;
    private boolean current;
}
