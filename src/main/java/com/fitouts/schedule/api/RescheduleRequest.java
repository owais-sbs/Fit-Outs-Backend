package com.fitouts.schedule.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

/**
 * One edit plus a request to re-solve. Sent by a Gantt bar drag or a duration change; the
 * server owns the resulting dates.
 */
@Getter
@Setter
public class RescheduleRequest {

    /** Omit to re-solve without editing anything, e.g. after a permit is approved. */
    private UUID activityUuid;

    /** Where the bar was dropped. Applied as a "start no earlier than" constraint. */
    private LocalDate newStartDate;

    private Integer durationWorkingDays;

    /** Removes a previously dragged or permit-imposed constraint. */
    private Boolean clearConstraint;

    private LocalDate projectStartDate;
}
