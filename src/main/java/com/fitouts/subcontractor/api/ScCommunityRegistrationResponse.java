package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScCommunityRegistrationResponse {

    private final UUID uuid;
    private final String authorityCode;
    private final String authorityName;
    private final String registrationNo;
    private final String expiryDate;
    private final String filePath;
}
