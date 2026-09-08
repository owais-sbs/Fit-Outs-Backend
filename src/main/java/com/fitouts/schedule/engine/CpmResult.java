package com.fitouts.schedule.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Outcome of one CPM solve. */
@Getter
@Setter
public class CpmResult {

    private List<CpmActivity> activities = new ArrayList<>();
    private LocalDate projectStart;
    private LocalDate projectFinish;
    private int totalWorkingDays;
    private int totalCalendarDays;

    /**
     * Critical chains, longest first. The spec asks for the three longest paths highlighted,
     * not just the single zero-float set, because near-critical chains are where fit-out
     * programmes actually slip.
     */
    private List<List<String>> criticalPaths = new ArrayList<>();

    /** Cycles, dangling predecessors and other logic faults found while solving. */
    private List<String> warnings = new ArrayList<>();

    public List<CpmActivity> criticalActivities() {
        return activities.stream().filter(CpmActivity::isCritical).toList();
    }
}
