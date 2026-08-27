package com.fitouts.resource.api;

import java.util.UUID;

import com.fitouts.resource.domain.ResourceKind;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceTypeResponse {
    private UUID uuid;
    private String name;
    private ResourceKind kind;
    private boolean active;
}
