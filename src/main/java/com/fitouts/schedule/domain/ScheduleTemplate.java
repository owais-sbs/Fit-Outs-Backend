package com.fitouts.schedule.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A reusable programme skeleton. A null {@code companyId} marks a system template that every
 * tenant can read but only the platform can edit.
 */
@Entity
@Table(name = "schedule_template")
@Getter
@Setter
public class ScheduleTemplate {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "project_type", length = 64)
    private String projectType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "target_calendar_days")
    private Integer targetCalendarDays;

    @Column(name = "target_working_days")
    private Integer targetWorkingDays;

    /**
     * What CPM actually produces from this template's own logic. Where it differs from
     * {@code targetWorkingDays}, the source document's headline figure and its dependency
     * grammar disagree, and the UI shows both rather than hiding the gap.
     */
    @Column(name = "computed_working_days")
    private Integer computedWorkingDays;

    @Column(name = "base_parameters_json", columnDefinition = "text")
    private String baseParametersJson;

    @Column(name = "work_week", length = 32)
    private String workWeek;

    @Column(name = "is_system_template", nullable = false)
    private boolean systemTemplate = true;

    @Column(name = "is_fast_track", nullable = false)
    private boolean fastTrack;

    @Column(name = "fast_track_conditions_json", columnDefinition = "text")
    private String fastTrackConditionsJson;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "data_quality_notes", columnDefinition = "text")
    private String dataQualityNotes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
