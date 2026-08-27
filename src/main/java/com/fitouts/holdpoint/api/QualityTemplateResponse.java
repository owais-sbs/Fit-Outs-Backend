package com.fitouts.holdpoint.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QualityTemplateResponse {
    private UUID companyId;
    private String activityType;
    private String checklistJson;
    private List<String> checklistItems;
    private OffsetDateTime updatedAt;
}
