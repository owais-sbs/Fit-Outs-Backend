package com.fitouts.approval.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JurisdictionRequest {

    private String emirate;
    private String communityName;
    private String buildingName;
    private String plotZone;
    private String regulatorAuthorityCode;
    private String masterDeveloperAuthorityCode;
    private String buildingManagementAuthorityCode;
    private String utilityAuthorityCode;
    private List<String> additionalAuthorityCodes;
    private Boolean verified;
    private String notes;
}
