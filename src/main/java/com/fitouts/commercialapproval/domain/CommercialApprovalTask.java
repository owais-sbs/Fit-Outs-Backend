package com.fitouts.commercialapproval.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.auth.domain.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "commercial_approval_task")
@Getter
@Setter
public class CommercialApprovalTask {

    @Id
    private UUID uuid;

    @Column(name = "run_uuid", nullable = false)
    private UUID runUuid;

    @Column(name = "step_uuid")
    private UUID stepUuid;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommercialApprovalTaskStatus status = CommercialApprovalTaskStatus.PENDING;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "reminder_sent_at")
    private OffsetDateTime reminderSentAt;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
