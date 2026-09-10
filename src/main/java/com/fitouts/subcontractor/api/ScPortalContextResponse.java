package com.fitouts.subcontractor.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScPortalContextResponse {

    private final String portalRole;
    private final String portalUserStatus;
    private final boolean canAccessCommercial;
    private final boolean canAccessExecution;
    private final boolean canAccessTendering;

    @JsonProperty("isOrgAdmin")
    private final boolean isOrgAdmin;

    private final boolean canAccessDocuments;
}
