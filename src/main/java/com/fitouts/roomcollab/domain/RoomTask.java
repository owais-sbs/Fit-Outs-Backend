package com.fitouts.roomcollab.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "room_tasks")
@Getter
@Setter
public class RoomTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "project_room_id", nullable = false)
    private UUID projectRoomId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 60)
    private RoomTaskType taskType = RoomTaskType.OTHER;

    @Column(name = "type_label", length = 120)
    private String typeLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RoomTaskStatus status = RoomTaskStatus.OPEN;

    @Column(name = "client_deadline")
    private OffsetDateTime clientDeadline;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "assignee_account_id")
    private Long assigneeAccountId;

    @Column(name = "first_sent_to_client_at")
    private OffsetDateTime firstSentToClientAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "client_approval_days")
    private Integer clientApprovalDays;

    @Column(name = "revision_count", nullable = false)
    private Integer revisionCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (taskType == null) taskType = RoomTaskType.OTHER;
        if (status == null) status = RoomTaskStatus.OPEN;
        if (revisionCount == null) revisionCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
