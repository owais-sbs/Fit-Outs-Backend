package com.fitouts.schedule.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/** Read-only CPM analysis of the live programme (same engine as preview/reschedule). */
@Data
@Builder
public class LiveCpmSnapshot {
    /** Primary critical path as activity UUIDs (first longest path). */
    @Builder.Default
    private List<UUID> criticalPath = new ArrayList<>();

    /** Up to three critical paths as activity UUID lists. */
    @Builder.Default
    private List<List<UUID>> criticalPaths = new ArrayList<>();

    /** Per-activity critical / float flags from the solve. */
    @Builder.Default
    private Map<UUID, Boolean> criticalByUuid = new HashMap<>();

    @Builder.Default
    private Map<UUID, Integer> totalFloatByUuid = new HashMap<>();

    @Builder.Default
    private Map<UUID, Integer> freeFloatByUuid = new HashMap<>();
}
