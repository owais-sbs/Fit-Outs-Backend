package com.fitouts.snag.domain;

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
@Table(name = "snag")
@Getter
@Setter
public class Snag {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column
    private String location;

    @Column(name = "project_room_id")
    private UUID projectRoomId;

    @Column(name = "activity_uuid")
    private UUID activityUuid;

    @Column(name = "photo_paths")
    private String photoPaths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SnagStatus status = SnagStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SnagSeverity severity = SnagSeverity.MEDIUM;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "raised_by")
    private Long raisedBy;

    @Column(name = "raised_by_client", nullable = false)
    private boolean raisedByClient;

    @Column(name = "assignee_account_id")
    private Long assigneeAccountId;

    @Column(name = "client_visible", nullable = false)
    private boolean clientVisible;

    @Column(name = "client_approved_at")
    private OffsetDateTime clientApprovedAt;

    @Column(name = "client_approved_by")
    private Long clientApprovedBy;

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
