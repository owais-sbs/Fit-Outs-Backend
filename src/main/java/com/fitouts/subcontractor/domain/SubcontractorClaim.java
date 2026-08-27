package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
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
@Table(name = "subcontractor_claim")
@Getter
@Setter
public class SubcontractorClaim {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "claimed_qty", nullable = false)
    private BigDecimal claimedQty = BigDecimal.ZERO;

    @Column(name = "planned_qty", nullable = false)
    private BigDecimal plannedQty = BigDecimal.ZERO;

    @Column
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubcontractorClaimStatus status = SubcontractorClaimStatus.DRAFT;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column
    private String reason;

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
