package com.fitouts.schedule.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

/** Preview / apply body for Mode 2 (BOQ) and Mode 3 (BLEND). */
@Getter
@Setter
public class ProgrammeBuildRequest {

    /** BOQ or BLEND */
    private String mode;

    private UUID templateUuid;
    private String templateCode;

    private java.time.LocalDate startDate;
    private UUID workCalendarUuid;

    /** Mode 2: duration per BOQ line. */
    private List<LineDuration> lineDurations = new ArrayList<>();

    /** Mode 3: confirmed matches (activityCode null = unmatched / no match). */
    private List<BoqMatch> matches = new ArrayList<>();

    /** Mode 3: durations for unmatched lines. */
    private List<LineDuration> unmatchedDurations = new ArrayList<>();

    /** Optional template params when mode=BLEND (same fields as SchedulePreviewRequest). */
    private Double areaSqft;
    private Integer roomCount;
    private Integer floorCount;
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer kitchens;
    private String finishLevel;
    private Integer crewCount;
    private Boolean occupiedBuilding;
    private java.util.Map<String, Boolean> scopeToggles = new java.util.LinkedHashMap<>();
    private java.util.Map<String, Double> quantities = new java.util.LinkedHashMap<>();
    private java.util.Map<String, Boolean> fastTrackAcknowledgements = new java.util.LinkedHashMap<>();

    @Getter
    @Setter
    public static class LineDuration {
        private UUID boqLineId;
        private Integer durationWorkingDays;
    }

    @Getter
    @Setter
    public static class BoqMatch {
        private UUID boqLineId;
        /** Null or blank = no match (unmatched line). */
        private String activityCode;
        /** AUTO_ACCEPTED or USER_CHANGED; optional on preview. */
        private String matchSource;
    }
}
