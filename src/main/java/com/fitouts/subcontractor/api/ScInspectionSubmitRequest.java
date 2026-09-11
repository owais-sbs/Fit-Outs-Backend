package com.fitouts.subcontractor.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScInspectionSubmitRequest {

    @NotBlank(message = "inspectionType is required")
    private String inspectionType;

    private String description;

    private Integer noticePeriodHours;

    private UUID activityUuid;

    private String attachments;
}
