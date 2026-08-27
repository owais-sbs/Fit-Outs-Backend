package com.fitouts.checklist.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SiteVisitRecordingResponse {

    private UUID uuid;
    private UUID siteVisitUuid;
    private String audioUrl;
    private Integer durationSeconds;
    private String transcript;
    private String aiSummary;
    private String processingStatus;
    private OffsetDateTime createdAt;
}
