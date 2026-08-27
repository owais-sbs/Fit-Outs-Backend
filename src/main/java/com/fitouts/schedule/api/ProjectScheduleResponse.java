package com.fitouts.schedule.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectScheduleResponse {
    private Long projectId;
    private boolean ganttPublishAllowed;
    private List<ScheduleActivityResponse> activities;
    private List<ScheduleDependencyResponse> dependencies;
    private List<ScheduleBaselineResponse> baselines;
    /** Activity UUIDs on the longest FS path by duration. */
    private List<UUID> criticalPath;
}
