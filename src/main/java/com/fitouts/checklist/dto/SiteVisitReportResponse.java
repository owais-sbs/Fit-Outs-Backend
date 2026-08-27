package com.fitouts.checklist.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SiteVisitReportResponse {

    private UUID uuid;
    private UUID siteVisitUuid;
    private String outcome;
    private String notes;
    private Long submittedBy;
    private OffsetDateTime submittedAt;
    private List<SiteVisitReportItemResponse> items;
    private Boolean clientAccountCreated;
    private Long clientAccountId;
    private String clientEmail;
    private Boolean inviteEmailSent;
    private List<SiteVisitRecordingResponse> recordings;

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setSiteVisitUuid(UUID siteVisitUuid) {
        this.siteVisitUuid = siteVisitUuid;
    }
}
