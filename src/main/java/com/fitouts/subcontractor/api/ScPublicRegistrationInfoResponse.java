package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScPublicRegistrationInfoResponse {

    private final UUID token;
    private final String contractorDisplayName;
    private final String expiresAt;
    private final boolean usable;
}
