package com.fitouts.approvalconfig.api;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PermitTypeResponse {
    private UUID id;
    private String permitCode;
    private String name;
    private String issuingBody;
    private String typicalTrigger;
    private String prerequisiteCases;
    private String slaWorkingDays;
    private String typicalValidity;
    private String deposit;
    private String renewable;
    private String blocksActivities;
    private boolean active;
    private LocalDateTime updatedAt;
}
