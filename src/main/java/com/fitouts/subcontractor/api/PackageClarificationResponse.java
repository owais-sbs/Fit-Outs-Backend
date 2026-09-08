package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageClarificationResponse {

    private UUID id;
    private UUID packageId;
    private UUID subcontractorId;
    private String subcontractorName;
    private Long authorAccountId;
    private String question;
    private String answer;
    private Long answeredBy;
    private OffsetDateTime answeredAt;
    private Boolean isPublicAddendum;
    private OffsetDateTime createdAt;
}
