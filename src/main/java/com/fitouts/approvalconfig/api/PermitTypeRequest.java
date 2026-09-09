package com.fitouts.approvalconfig.api;

import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermitTypeRequest {
    private String permitCode;
    private String name;
    private String issuingBody;
    private String typicalTrigger;
    private String triggerType;
    private String prerequisiteCases;
    private String slaWorkingDays;
    private String typicalValidity;
    private String deposit;
    private String renewable;
    private String blocksActivities;
    private Boolean active;
    private List<UUID> scopeTagIds;
    private List<UUID> propertyTypeIds;
    private List<UUID> projectNatureIds;
}
