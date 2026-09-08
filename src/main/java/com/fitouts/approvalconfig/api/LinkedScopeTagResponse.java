package com.fitouts.approvalconfig.api;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LinkedScopeTagResponse {
    private UUID id;
    private String code;
    private String name;
    private String createdByName;
    private LocalDateTime createdAt;
}
