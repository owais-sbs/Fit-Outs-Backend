package com.fitouts.schedule.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

/** Parameters for a template preview or apply. */
@Getter
@Setter
public class SchedulePreviewRequest {

    private UUID templateUuid;
    private String templateCode;

    private LocalDate startDate;

    private Double areaSqft;
    private Integer roomCount;
    private Integer floorCount;
    private String finishLevel;
    private Integer crewCount;
    private Boolean occupiedBuilding;

    private UUID workCalendarUuid;

    /** Scope questions by toggle code. Absent means the template's default applies. */
    private Map<String, Boolean> scopeToggles = new LinkedHashMap<>();

    /** Measured quantities keyed by scaling driver, when the BOQ has them. */
    private Map<String, Double> quantities = new LinkedHashMap<>();

    /**
     * The nine fast-track conditions, acknowledged by name. Template A2 will not publish
     * without them.
     */
    private Map<String, Boolean> fastTrackAcknowledgements = new LinkedHashMap<>();
}
