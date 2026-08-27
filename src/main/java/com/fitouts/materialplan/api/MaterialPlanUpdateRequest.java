package com.fitouts.materialplan.api;

import java.util.List;

import com.fitouts.materialplan.domain.MaterialPlanStatus;

import lombok.Data;

@Data
public class MaterialPlanUpdateRequest {
    private MaterialPlanStatus status;
    private List<MaterialPlanLineRequest> lines;
}
