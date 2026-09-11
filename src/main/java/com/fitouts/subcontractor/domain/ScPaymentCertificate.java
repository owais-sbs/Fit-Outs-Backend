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
@Table(name = "sc_payment_certificate")
@Getter
@Setter
public class ScPaymentCertificate {

    @Id
    private UUID uuid;

    @Column(name = "claim_uuid", nullable = false)
    private UUID claimUuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "organization_uuid")
    private UUID organizationUuid;

    @Column(name = "certified_value", precision = 18, scale = 2)
    private BigDecimal certifiedValue;

    @Column(name = "retention_held", precision = 18, scale = 2)
    private BigDecimal retentionHeld;

    @Column(name = "back_charges_applied", precision = 18, scale = 2)
    private BigDecimal backChargesApplied;

    @Column(name = "net_payable", precision = 18, scale = 2)
    private BigDecimal netPayable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScPaymentCertificateStatus status = ScPaymentCertificateStatus.DRAFT;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "accounting_ref", length = 128)
    private String accountingRef;

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
