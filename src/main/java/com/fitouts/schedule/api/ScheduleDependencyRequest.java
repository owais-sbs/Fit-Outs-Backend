package com.fitouts.schedule.api;

import java.util.UUID;

import lombok.Data;

@Data
public class ScheduleDependencyRequest {
    private UUID predecessorUuid;
    private UUID successorUuid;
}
