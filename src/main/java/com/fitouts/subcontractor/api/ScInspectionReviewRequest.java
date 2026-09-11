package com.fitouts.subcontractor.api;

import com.fitouts.subcontractor.domain.ScInspectionStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScInspectionReviewRequest {

    @NotNull(message = "status is required")
    private ScInspectionStatus status;

    private String resultNotes;

    private String evidencePaths;
}
