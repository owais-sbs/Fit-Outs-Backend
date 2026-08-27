package com.fitouts.schedule.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleDependencyResponse {
    private UUID uuid;
    private UUID predecessorUuid;
    private UUID successorUuid;
    private String dependencyType;
}
