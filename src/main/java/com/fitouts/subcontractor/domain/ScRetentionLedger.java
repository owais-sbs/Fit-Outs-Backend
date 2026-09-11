package com.fitouts.subcontractor.domain;

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
@Table(name = "sc_retention_ledger")
@Getter
@Setter
public class ScRetentionLedger {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "amount_held", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountHeld;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "defect_liability_end")
    private LocalDate defectLiabilityEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScRetentionStatus status = ScRetentionStatus.HELD;

    @Column(name = "certificate_uuid")
    private UUID certificateUuid;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
