package com.fitouts.checklist.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitReportItemRequest {

    @NotBlank
    private String response;

    private String remarks;
    private List<String> photoUrls = new ArrayList<>();
}
