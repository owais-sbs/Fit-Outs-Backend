package com.fitouts.approvalconfig.api;

import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JurisdictionPackRequest {
    private String code;
    private String name;
    private String description;
    private UUID communityAuthorityId;
    private UUID primaryAuthorityId;
    private Boolean selectable;
    private Boolean active;
    private List<PackPermitLineRequest> permits;
}
