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
    /** Activity UUIDs on the primary critical path (CpmEngine). */
    private List<UUID> criticalPath;
    /** Up to three critical paths from CpmEngine (UUID lists). */
    private List<List<UUID>> criticalPaths;
}
