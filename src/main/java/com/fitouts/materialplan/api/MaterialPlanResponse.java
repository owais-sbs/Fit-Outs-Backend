package com.fitouts.materialplan.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.materialplan.domain.MaterialPlanStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialPlanResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private MaterialPlanStatus status;
    private UUID generatedFromBoqId;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private List<MaterialPlanLineResponse> lines;
}
