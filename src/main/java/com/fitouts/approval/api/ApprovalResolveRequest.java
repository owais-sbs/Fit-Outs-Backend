package com.fitouts.approval.api;

import lombok.Getter;
import lombok.Setter;

/**
 * Overrides for a resolve or generate call. When a field is null the value stored on the
 * project is used, so the PM can preview "what if this were in Palm Jumeirah" without
 * editing the project first.
 */
@Getter
@Setter
public class ApprovalResolveRequest {

    private String emirate;
    private String communityName;
    private String buildingName;
    private String plotZone;
    private ProjectScopeToggles scope;
    /** Persist the location and scope back onto the project as part of the call. */
    private Boolean saveToProject;
}
