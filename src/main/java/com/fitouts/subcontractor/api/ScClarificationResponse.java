package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScClarificationResponse {
    private UUID uuid;
    private UUID organizationUuid;
    private String question;
    private String answer;
    private boolean material;
    private OffsetDateTime issuedToAllAt;
    private OffsetDateTime createdAt;
}
