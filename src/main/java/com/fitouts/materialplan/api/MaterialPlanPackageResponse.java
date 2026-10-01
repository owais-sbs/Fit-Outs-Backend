package com.fitouts.materialplan.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialPlanPackageResponse {
    private UUID id;
    private String name;
    private int sortOrder;
}
