package com.fitouts.checklist.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitReportItemRequest {

    @NotNull
    private UUID templateItemUuid;

    private String response;
    private String remarks;
    private List<String> photoUrls = new ArrayList<>();

    public UUID getTemplateItemUuid() {
        return templateItemUuid;
    }
}
