package com.fitouts.schedule.domain;

import java.time.LocalDate;
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
 * The applied instance: which template, with which parameters, produced this project's
 * programme. One row per project, rewritten on each apply.
 */
@Entity
@Table(name = "project_schedule")
@Getter
@Setter
public class ProjectSchedule {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "template_uuid")
    private UUID templateUuid;

    @Column(name = "template_code", length = 32)
    private String templateCode;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "parameters_json", columnDefinition = "text")
    private String parametersJson;

    @Column(name = "toggles_json", columnDefinition = "text")
    private String togglesJson;

    @Column(name = "work_calendar_uuid")
    private UUID workCalendarUuid;

    @Column(name = "data_date")
    private LocalDate dataDate;

    @Column(name = "baseline_saved_at")
    private OffsetDateTime baselineSavedAt;

    @Column(name = "baseline_finish_date")
    private LocalDate baselineFinishDate;

    @Column(name = "current_finish_date")
    private LocalDate currentFinishDate;

    @Column(name = "computed_working_days")
    private Integer computedWorkingDays;

    /** Template A2 cannot publish until the fast-track conditions are acknowledged in writing. */
    @Column(name = "fast_track_ack_json", columnDefinition = "text")
    private String fastTrackAckJson;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

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
