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
public class ChecklistTemplateResponse {

    private UUID uuid;
    private String name;
    private String description;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ChecklistTemplateItemResponse> items;

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
}
