package com.fitouts.resource.api;

import com.fitouts.resource.domain.ResourceKind;

import lombok.Data;

@Data
public class ResourceTypeRequest {
    private String name;
    private ResourceKind kind;
    private Boolean active;
}
