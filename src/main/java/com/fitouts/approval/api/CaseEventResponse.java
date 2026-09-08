package com.fitouts.approval.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseEventResponse {

    private UUID uuid;
    private String fromStatus;
    private String toStatus;
    private String action;
    private String detail;
    private Long actorAccountId;
    private OffsetDateTime createdAt;
}
