package com.fitouts.approval.domain;

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
 * Resolver lookup: which authorities own a given community or building.
 * Seed rows are derived from authority area prose and land unverified.
 */
@Entity
@Table(name = "jurisdiction")
@Getter
@Setter
public class Jurisdiction {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 64)
    private String emirate;

    @Column(name = "community_name", nullable = false, length = 180)
    private String communityName;

    /** Lower-cased, punctuation-stripped community name used for matching. */
    @Column(name = "community_key", nullable = false, length = 180)
    private String communityKey;

    @Column(name = "building_name", length = 180)
    private String buildingName;

    @Column(name = "plot_zone", length = 180)
    private String plotZone;

    @Column(name = "regulator_authority_code", length = 32)
    private String regulatorAuthorityCode;

    @Column(name = "master_developer_authority_code", length = 32)
    private String masterDeveloperAuthorityCode;

    @Column(name = "building_management_authority_code", length = 32)
    private String buildingManagementAuthorityCode;

    @Column(name = "utility_authority_code", length = 32)
    private String utilityAuthorityCode;

    @Column(name = "additional_authority_codes", columnDefinition = "TEXT")
    private String additionalAuthorityCodes;

    @Column(columnDefinition = "TEXT")
    private String aliases;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "derived_from")
    private String derivedFrom;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
