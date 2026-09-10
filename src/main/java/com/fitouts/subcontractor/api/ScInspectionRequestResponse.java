package com.fitouts.subcontractor.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subcontractor.domain.ScInspectionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScInspectionRequestResponse {
    private UUID uuid;
    private Long projectId;
    private UUID packageUuid;
    private UUID organizationUuid;
    private UUID activityUuid;
    private String inspectionType;
    private OffsetDateTime requestedAt;
    private Integer noticePeriodHours;
    private String description;
    private String attachments;
    private ScInspectionStatus status;
    private String resultNotes;
    private String evidencePaths;
    private Long inspectedBy;
    private OffsetDateTime inspectedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
