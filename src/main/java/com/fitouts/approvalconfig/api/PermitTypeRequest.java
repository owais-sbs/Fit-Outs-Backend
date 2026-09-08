package com.fitouts.approvalconfig.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermitTypeRequest {
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
    private Boolean active;
}
