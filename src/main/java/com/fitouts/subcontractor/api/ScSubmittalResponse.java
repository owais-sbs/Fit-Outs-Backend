package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScSubmittalResponse {
    private UUID uuid;
    private UUID packageUuid;
    private Long projectId;
    private UUID organizationUuid;
    private String title;
    private String submittalType;
    private int revisionNo;
    private String reviewCode;
    private String status;
    private String filePaths;
    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;
}
