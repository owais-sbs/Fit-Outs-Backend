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

    // --- CPM engine columns (Module 43) -------------------------------------

    /** Stable code from the template, e.g. G120. How dependencies, packages and permits find it. */
    @Column(name = "activity_code", length = 32)
    private String activityCode;

    @Column(name = "wbs_phase", length = 120)
    private String wbsPhase;

    /**
     * Duration in working days. {@code startDate}/{@code endDate} are derived from this by the
     * engine, so this is the value to change when a duration changes, not the dates.
     */
    @Column(name = "duration_working_days")
    private Integer durationWorkingDays;

    @Column(name = "scaling_method", length = 16)
    private String scalingMethod;

    @Column(name = "trade_package_code", length = 32)
    private String tradePackageCode;

    @Column(name = "is_milestone", nullable = false)
    private boolean milestone;

    @Column(name = "is_locked_duration", nullable = false)
    private boolean lockedDuration;

    @Column(name = "constraint_note", columnDefinition = "text")
    private String constraintNote;

    @Column(name = "early_start")
    private LocalDate earlyStart;

    @Column(name = "early_finish")
    private LocalDate earlyFinish;

    @Column(name = "late_start")
    private LocalDate lateStart;

    @Column(name = "late_finish")
    private LocalDate lateFinish;

    @Column(name = "total_float")
    private Integer totalFloat;

    @Column(name = "free_float")
    private Integer freeFloat;

    @Column(name = "is_critical", nullable = false)
    private boolean critical;

    /** Set when an unapproved or expired blocking permit is holding this activity back. */
    @Column(name = "constrained_by_case_uuid")
    private UUID constrainedByCaseUuid;

    @Column(name = "constraint_start_date")
    private LocalDate constraintStartDate;

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
