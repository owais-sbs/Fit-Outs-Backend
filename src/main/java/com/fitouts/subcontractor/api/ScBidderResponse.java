package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScBidderResponse {
    private UUID uuid;
    private UUID organizationUuid;
    private String organizationName;
    private String status;
    private OffsetDateTime invitedAt;
    private OffsetDateTime viewedAt;
    private boolean eligible;
    private String eligibilityJson;
}
