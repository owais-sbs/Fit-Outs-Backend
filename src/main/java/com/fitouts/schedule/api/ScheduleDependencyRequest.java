package com.fitouts.schedule.api;

import java.util.UUID;

import lombok.Data;

@Data
public class ScheduleDependencyRequest {
    private UUID predecessorUuid;
    private UUID successorUuid;
    /** FS, SS, FF or SF. Defaults to FS when blank. */
    private String dependencyType;
    /** Working-day lag after the predecessor constraint (default 0). */
    private Integer lagWorkingDays;
}
