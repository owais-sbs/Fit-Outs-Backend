package com.fitouts.schedule.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A subcontract trade package in the catalogue, e.g. PKG-ALU "Aluminium, glazing and steel".
 *
 * <p>Global rows (null company) come from the seed; a tenant can add its own.
 */
@Entity
@Table(name = "trade_package")
@Getter
@Setter
public class TradePackage {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "typical_boq_sections", columnDefinition = "text")
    private String typicalBoqSections;

    @Column(name = "special_licence_required")
    private String specialLicenceRequired;

    @Column(name = "typical_retention", length = 64)
    private String typicalRetention;

    @Column(name = "typical_payment_terms", length = 64)
    private String typicalPaymentTerms;

    @Column(name = "match_keywords", columnDefinition = "text")
    private String matchKeywords;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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
