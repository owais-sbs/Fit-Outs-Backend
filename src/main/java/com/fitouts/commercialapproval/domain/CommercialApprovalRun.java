package com.fitouts.commercialapproval.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

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
@Table(name = "commercial_approval_run")
@Getter
@Setter
public class CommercialApprovalRun {

    @Id
    private UUID uuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "project_id")
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private CommercialEventType eventType;

    @Column(name = "entity_uuid", nullable = false)
    private UUID entityUuid;

    @Column(name = "matrix_uuid")
    private UUID matrixUuid;

    @Column(name = "band_uuid")
    private UUID bandUuid;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommercialApprovalRunStatus status = CommercialApprovalRunStatus.IN_PROGRESS;

    @Column(name = "current_step_order", nullable = false)
    private int currentStepOrder = 1;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }
}
