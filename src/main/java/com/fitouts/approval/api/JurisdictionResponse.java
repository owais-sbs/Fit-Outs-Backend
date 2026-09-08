package com.fitouts.approval.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JurisdictionResponse {

    private UUID uuid;
    private String emirate;
    private String communityName;
    private String communityKey;
    private String buildingName;
    private String plotZone;
    private String regulatorAuthorityCode;
    private String regulatorAuthorityName;
    private String masterDeveloperAuthorityCode;
    private String masterDeveloperAuthorityName;
    private String buildingManagementAuthorityCode;
    private String utilityAuthorityCode;
    private List<String> additionalAuthorityCodes;
    /** Derived rows start false. VetroBuild confirms them in the admin library. */
    private boolean verified;
    private String derivedFrom;
    private String notes;
    private boolean tenantOwned;
}
