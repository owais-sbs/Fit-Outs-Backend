package com.fitouts.approvalconfig.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProjectPermitCaseResponse {
    private UUID id;
    private UUID permitTypeId;
    private String permitCode;
    private String permitName;
    private UUID issuingAuthorityId;
    private String issuingAuthorityName;
    private String status;
    private String slaWorkingDays;
    private String blocksActivities;
    private String inclusionRule;
    private int sortOrder;
    private String notes;
    @Builder.Default
    private List<ProjectPermitDocumentResponse> documents = new ArrayList<>();
}
