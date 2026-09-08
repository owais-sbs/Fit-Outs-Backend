package com.fitouts.approval.domain;

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

/**
 * An authority, master developer, building management body or utility.
 * A null companyId marks a row from the global seed catalogue.
 */
@Entity
@Table(name = "authority")
@Getter
@Setter
public class Authority {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthorityType type;

    @Column(length = 64)
    private String emirate;

    @Column(name = "jurisdiction_areas", columnDefinition = "TEXT")
    private String jurisdictionAreas;

    @Column(name = "permits_issued_typical", columnDefinition = "TEXT")
    private String permitsIssuedTypical;

    @Column(name = "submission_channel", columnDefinition = "TEXT")
    private String submissionChannel;

    @Column(name = "portal_url", columnDefinition = "TEXT")
    private String portalUrl;

    @Column(name = "contact_json", columnDefinition = "TEXT")
    private String contactJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "verified_by", length = 120)
    private String verifiedBy;

    @Column(name = "verified_date")
    private LocalDate verifiedDate;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

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
