package com.fitouts.materialplan.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialPlanWorkItemSectionResponse {
    private UUID workItemId;
    private String workItemName;
    private int sortOrder;
    private List<MaterialPlanLineResponse> lines;
}
