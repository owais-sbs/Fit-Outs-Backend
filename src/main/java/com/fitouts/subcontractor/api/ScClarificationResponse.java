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
    /** Set on cross-RFQ inbox responses. */
    private UUID packageUuid;
    private String packageName;
    private Long projectId;
    private String projectName;
}
