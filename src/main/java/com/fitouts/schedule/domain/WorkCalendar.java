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

@Entity
@Table(name = "work_calendar")
@Getter
@Setter
public class WorkCalendar {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    /** ISO day numbers, Monday = 1. UAE default "6,7,1,2,3,4" is Saturday to Thursday. */
    @Column(name = "working_days", nullable = false, length = 32)
    private String workingDays = "6,7,1,2,3,4";

    /**
     * When enabled, working days inside the summer window count as 5.5/8 of a normal day
     * (midday removes 2.5h from an 8h day) for duration consumption in the CPM engine.
     */
    @Column(name = "summer_break_enabled", nullable = false)
    private boolean summerBreakEnabled = true;

    @Column(name = "summer_break_start", length = 8)
    private String summerBreakStart = "06-15";

    @Column(name = "summer_break_end", length = 8)
    private String summerBreakEnd = "09-15";

    @Column(name = "summer_break_window", length = 32)
    private String summerBreakWindow = "12:30-15:00";

    @Column(name = "ramadan_hours_note", columnDefinition = "text")
    private String ramadanHoursNote;

    @Column(name = "community_restriction_note", columnDefinition = "text")
    private String communityRestrictionNote;

    @Column(name = "is_default", nullable = false)
    private boolean defaultCalendar;

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
