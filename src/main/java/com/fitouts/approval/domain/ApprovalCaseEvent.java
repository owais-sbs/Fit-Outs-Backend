package com.fitouts.approval.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Audit trail entry for a case. Every status move and material action lands here. */
@Entity
@Table(name = "approval_case_event")
@Getter
@Setter
public class ApprovalCaseEvent {

    @Id
    private UUID uuid;

    @Column(name = "case_uuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", length = 32)
    private String toStatus;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
