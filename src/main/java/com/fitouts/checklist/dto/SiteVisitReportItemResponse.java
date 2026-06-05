package com.fitouts.checklist.dto;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SiteVisitReportItemResponse {

    private UUID uuid;
    private UUID templateItemUuid;
    private String response;
    private String remarks;
    private List<String> photoUrls;

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public void setTemplateItemUuid(UUID templateItemUuid) {
        this.templateItemUuid = templateItemUuid;
    }
}
