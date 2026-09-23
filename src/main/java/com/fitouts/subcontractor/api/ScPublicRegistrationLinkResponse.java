package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScPublicRegistrationLinkResponse {

    private final UUID token;
    private final String path;
    private final String expiresAt;
    private final boolean regenerated;
}
