package com.fitouts.approvalconfig.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class JurisdictionPackResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private UUID communityAuthorityId;
    private String communityAuthorityName;
    private UUID primaryAuthorityId;
    private String primaryAuthorityName;
    private boolean selectable;
    private boolean active;
    @Builder.Default
    private List<PackPermitResponse> permits = new ArrayList<>();
}
