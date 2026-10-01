package com.fitouts.materialplan.api;

import java.util.UUID;

import lombok.Data;

@Data
public class MaterialPlanPackageRequest {
    /** Client-stable id so lines can reference the package in the same payload. */
    private UUID id;
    private String name;
    private Integer sortOrder;
}
