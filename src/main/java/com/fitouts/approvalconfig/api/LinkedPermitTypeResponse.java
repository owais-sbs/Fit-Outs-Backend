package com.fitouts.approvalconfig.api;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LinkedPermitTypeResponse {
    private UUID id;
    private String permitCode;
    private String name;
    private String createdByName;
    private LocalDateTime createdAt;
}
