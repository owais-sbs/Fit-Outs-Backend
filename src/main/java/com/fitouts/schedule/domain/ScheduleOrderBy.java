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
 * A backward-scheduled procurement deadline: the last date an item can be ordered and still
 * arrive before its install activity starts. An overdue row means the programme is already
 * undeliverable, which is why publish refuses to proceed on one.
 */
@Entity
@Table(name = "schedule_order_by")
@Getter
@Setter
public class ScheduleOrderBy {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "lead_time_calendar_days", nullable = false)
    private int leadTimeCalendarDays;

    @Column(name = "install_activity_code", length = 32)
    private String installActivityCode;

    @Column(name = "install_activity_uuid")
    private UUID installActivityUuid;

    @Column(name = "install_start_date")
    private LocalDate installStartDate;

    @Column(name = "order_by_date", nullable = false)
    private LocalDate orderByDate;

    @Column(name = "is_overdue", nullable = false)
    private boolean overdue;

    @Column(name = "risk_note", columnDefinition = "text")
    private String riskNote;

    @Column(name = "site_info_needed", columnDefinition = "text")
    private String siteInfoNeeded;

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
