package com.fitouts.approval.api;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApprovalResolveResponse {

    private Long projectId;
    private String emirate;
    private String communityName;
    private String buildingName;
    private String plotZone;
    /** True when the community was found in the jurisdiction table. */
    private boolean jurisdictionMatched;
    /** True when the matched jurisdiction row is still a derived, unconfirmed guess. */
    private boolean jurisdictionUnverified;
    private String matchNote;
    private List<ResolvedAuthorityView> authorities;
    private List<ResolvedCaseView> cases;
    private List<String> warnings;
    /** Union of scope tags on work items linked to the latest approved BOQ. */
    private List<DerivedScopeTagView> derivedScopeTags;
    private boolean hasApprovedBoq;
    private String scopeNote;
}
