package com.fitouts.schedule.api;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RescheduleResponse {

    private LocalDate projectStart;
    private LocalDate projectFinish;
    private int workingDays;

    private LocalDate previousFinish;
    /** Positive when the finish slipped later. */
    private Long finishMovedByDays;

    private List<String> criticalActivityCodes;
    private List<List<String>> criticalPaths;

    private int packagesUpdated;
    private int approvalTargetsUpdated;

    /** Edits the engine declined, such as shrinking a locked cure period. */
    private List<String> refusals;
    private List<String> warnings;
}
