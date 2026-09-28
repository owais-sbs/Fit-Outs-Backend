package com.fitouts.schedule.domain;

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
@Table(name = "schedule_duration_extension")
@Getter
@Setter
public class ScheduleDurationExtension {

    @Id
    private UUID uuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "activity_uuid", nullable = false)
    private UUID activityUuid;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "current_duration_working_days", nullable = false)
    private Integer currentDurationWorkingDays;

    @Column(name = "requested_duration_working_days", nullable = false)
    private Integer requestedDurationWorkingDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "delay_reason_code", nullable = false, length = 40)
    private DelayReasonCode delayReasonCode;

    @Column(name = "delay_reason_text")
    private String delayReasonText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleDurationExtensionStatus status = ScheduleDurationExtensionStatus.PENDING;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_notes")
    private String decisionNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
