package com.fitouts.schedule.engine;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * One node in the network. Deliberately a plain mutable holder rather than a JPA entity: the
 * engine runs against these for previews that are never persisted, and against copies of live
 * activities when rescheduling.
 */
@Getter
@Setter
public class CpmActivity {

    private String code;
    private String name;
    private String wbsPhase;
    private String tradePackageCode;

    private int durationWorkingDays = 1;
    private boolean milestone;
    private boolean lockedDuration;
    private String constraintNote;

    /**
     * Earliest the activity may begin regardless of logic: an unapproved permit, a client
     * access date, a fixed handover. The forward pass never schedules earlier than this.
     */
    private LocalDate constraintStart;
    private String constraintSource;

    private LocalDate earlyStart;
    private LocalDate earlyFinish;
    private LocalDate lateStart;
    private LocalDate lateFinish;
    private int totalFloat;
    private int freeFloat;
    private boolean critical;

    /** Carried through so the caller can map results back onto persisted rows. */
    private Object ref;

    public int effectiveDuration() {
        return milestone ? 0 : Math.max(1, durationWorkingDays);
    }
}
