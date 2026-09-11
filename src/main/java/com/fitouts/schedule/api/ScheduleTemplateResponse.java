package com.fitouts.schedule.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScheduleTemplateResponse {

    private UUID uuid;
    private String code;
    private String name;
    private String projectType;
    private String description;

    /** The source document's headline programme length. */
    private Integer targetWorkingDays;
    private Integer targetCalendarDays;

    /** What CPM produces from this template's own dependency logic. */
    private Integer computedWorkingDays;

    /** Populated when the two disagree, so nobody quotes a number the engine will not deliver. */
    private String varianceNote;

    private boolean systemTemplate;
    private boolean fastTrack;
    private List<String> fastTrackConditions;
    private String workWeek;
    private int version;
    private String dataQualityNotes;

    private int activityCount;
    private int dependencyCount;
    private int procurementItemCount;

    private List<String> scopeToggleCodes;
    private BaseParameters baseParameters;

    @Getter
    @Builder
    public static class BaseParameters {
        private Double areaSqft;
        private Integer roomCount;
        private Integer floorCount;
        private Integer bedrooms;
        private Integer bathrooms;
        private Integer kitchens;
        private String finishLevel;
        private Integer crewCount;
    }
}
