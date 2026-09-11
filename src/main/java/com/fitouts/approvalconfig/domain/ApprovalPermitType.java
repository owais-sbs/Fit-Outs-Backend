package com.fitouts.approvalconfig.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "approval_permit_types")
@Getter
@Setter
public class ApprovalPermitType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "permit_code", nullable = false, length = 40)
    private String permitCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "issuing_body", length = 120)
    private String issuingBody;

    /**
     * Structured issuer lookup. Null means the catalogue row is incomplete.
     *
     * <p>{@link PermitAuthorityMechanisms#FIXED} links {@link #fixedAuthorityId} to one
     * named body. Seeded FIXED permits are Dubai-specific; do not add per-emirate
     * switching here — another emirate is a new Permit Type row.
     */
    @Column(name = "authority_resolution_mechanism", length = 40)
    private String authorityResolutionMechanism;

    @Column(name = "fixed_authority_id")
    private UUID fixedAuthorityId;

    @Column(name = "inherit_authority_from_permit_code", length = 40)
    private String inheritAuthorityFromPermitCode;

    @Column(name = "resolution_mode", nullable = false, length = 40)
    private String resolutionMode = PermitResolutionModes.ANY_ONE_APPLIES;

    @Column(name = "resolution_mode_confirmed_by", length = 120)
    private String resolutionModeConfirmedBy;

    @Column(name = "resolution_mode_confirmed_at")
    private LocalDateTime resolutionModeConfirmedAt;

    @Column(name = "allow_internal_hse_signoff", nullable = false)
    private boolean allowInternalHseSignoff = false;

    @Column(name = "typical_trigger", columnDefinition = "TEXT")
    private String typicalTrigger;

    @Column(name = "trigger_type", nullable = false, length = 40)
    private String triggerType = PermitTriggerTypes.SCOPE_TAG;

    @Column(name = "prerequisite_cases", columnDefinition = "TEXT")
    private String prerequisiteCases;

    @Column(name = "sla_working_days", length = 40)
    private String slaWorkingDays;

    @Column(name = "typical_validity", length = 80)
    private String typicalValidity;

    @Column(length = 80)
    private String deposit;

    @Column(length = 20)
    private String renewable;

    @Column(name = "blocks_activities", columnDefinition = "TEXT")
    private String blocksActivities;

    private boolean active = true;
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
