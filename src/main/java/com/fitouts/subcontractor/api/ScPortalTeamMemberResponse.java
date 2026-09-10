package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScPortalTeamMemberResponse {

    private final UUID uuid;
    private final Long accountId;
    private final String fullName;
    private final String email;
    private final String portalRole;
    private final String status;
}
