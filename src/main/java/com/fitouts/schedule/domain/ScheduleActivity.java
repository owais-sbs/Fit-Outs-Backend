package com.fitouts.schedule.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "schedule_activity")
@Getter
@Setter
public class ScheduleActivity {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "percent_complete", nullable = false)
    private int percentComplete;

    @Column(nullable = false)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "parent_uuid")
    private UUID parentUuid;

    @Column(name = "project_room_id")
    private UUID projectRoomId;

    @Column(name = "room_task_id")
    private UUID roomTaskId;

    @Column(name = "assignee_account_id")
    private Long assigneeAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false)
    private SchedulePublishStatus publishStatus = SchedulePublishStatus.DRAFT;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "delay_reason", length = 64)
    private String delayReason;

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
