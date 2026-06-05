package com.fitouts.checklist.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitReportRequest {

    @NotBlank
    private String outcome;

    private String notes;
    private Long submittedBy;

    @NotEmpty
    private List<@Valid SiteVisitReportItemRequest> items;
}
