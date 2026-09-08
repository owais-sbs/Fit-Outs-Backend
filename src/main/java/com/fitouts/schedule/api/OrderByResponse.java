package com.fitouts.schedule.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderByResponse {

    private UUID uuid;
    private String itemName;
    private int leadTimeCalendarDays;
    private String installActivityCode;
    private UUID installActivityUuid;
    private LocalDate installStartDate;
    private LocalDate orderByDate;
    private boolean overdue;

    /** Negative once the order-by date has passed. */
    private Long daysToOrderBy;

    private String riskNote;
    private String siteInfoNeeded;
}
