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
@Table(name = "sc_back_charge")
@Getter
@Setter
public class ScBackCharge {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(name = "charge_type", length = 64)
    private String chargeType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "evidence_paths", columnDefinition = "TEXT")
    private String evidencePaths;

    @Column(name = "raised_by")
    private Long raisedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(nullable = false)
    private boolean disputed;

    @Column(name = "applied_to_certificate_uuid")
    private UUID appliedToCertificateUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScBackChargeStatus status = ScBackChargeStatus.OPEN;

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
