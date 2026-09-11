package com.fitouts.approvalconfig.api;

import java.time.LocalDateTime;
import java.util.List;
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
    private String triggerType;
    private String prerequisiteCases;
    private String slaWorkingDays;
    private String typicalValidity;
    private String deposit;
    private String renewable;
    private String blocksActivities;
    private boolean active;
    private LocalDateTime updatedAt;
    private List<UUID> scopeTagIds;
    private List<LinkedScopeTagResponse> scopeTags;
    private List<UUID> propertyTypeIds;
    private List<CatalogLinkResponse> propertyTypes;
    private List<UUID> projectNatureIds;
    private List<CatalogLinkResponse> projectNatures;
    private boolean missingPrerequisite;
    private String authorityResolutionMechanism;
    private UUID fixedAuthorityId;
    private String fixedAuthorityCode;
    private String fixedAuthorityName;
    private String inheritAuthorityFromPermitCode;
    private String resolutionMode;
    private String resolutionModeConfirmedBy;
    private LocalDateTime resolutionModeConfirmedAt;
    private boolean resolutionModeNeedsReview;
    private boolean authorityResolutionIncomplete;
    private boolean allowInternalHseSignoff;
    private List<String> candidateAuthorityRoles;
}
