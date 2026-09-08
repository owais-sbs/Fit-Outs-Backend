package com.fitouts.approval.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Catalogue entry for one kind of permit or case. Seed SLAs and validities arrive as
 * text ranges ("5-15", "30-90 days"), so both bounds and the original string are kept.
 */
@Entity
@Table(name = "permit_type")
@Getter
@Setter
public class PermitType {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "authority_code", length = 32)
    private String authorityCode;

    @Column(name = "authority_type", length = 32)
    private String authorityType;

    @Column(name = "typical_trigger", columnDefinition = "TEXT")
    private String typicalTrigger;

    @Column(name = "trigger_rule_json", columnDefinition = "TEXT")
    private String triggerRuleJson;

    @Column(name = "prerequisite_permit_codes", columnDefinition = "TEXT")
    private String prerequisitePermitCodes;

    @Column(name = "prerequisite_notes", columnDefinition = "TEXT")
    private String prerequisiteNotes;

    @Column(name = "indicative_sla_days_min")
    private Integer indicativeSlaDaysMin;

    @Column(name = "indicative_sla_days_max")
    private Integer indicativeSlaDaysMax;

    @Column(name = "indicative_sla_raw", length = 64)
    private String indicativeSlaRaw;

    @Column(name = "actual_median_sla_days")
    private Integer actualMedianSlaDays;

    @Column(name = "completed_case_count", nullable = false)
    private int completedCaseCount = 0;

    @Column(name = "validity_days_min")
    private Integer validityDaysMin;

    @Column(name = "validity_days_max")
    private Integer validityDaysMax;

    @Column(name = "validity_raw", length = 64)
    private String validityRaw;

    @Column(name = "has_deposit", nullable = false)
    private boolean hasDeposit = false;

    /** The seed's "Sometimes" — a deposit may apply, so the PRO is prompted rather than assumed. */
    @Column(name = "deposit_conditional", nullable = false)
    private boolean depositConditional = false;

    @Column(name = "deposit_amount_indicative")
    private BigDecimal depositAmountIndicative;

    @Column(name = "fee_indicative")
    private BigDecimal feeIndicative;

    @Column(name = "is_renewable", nullable = false)
    private boolean renewable = false;

    @Column(name = "blocks_activities_raw", columnDefinition = "TEXT")
    private String blocksActivitiesRaw;

    @Column(name = "blocks_activity_codes", columnDefinition = "TEXT")
    private String blocksActivityCodes;

    @Column(name = "required_document_codes", columnDefinition = "TEXT")
    private String requiredDocumentCodes;

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

    /**
     * Planning SLA. Falls back to the seeded estimate until enough cases have closed
     * for this authority to trust the observed median.
     */
    public Integer planningSlaDays() {
        if (actualMedianSlaDays != null && completedCaseCount >= 10) {
            return actualMedianSlaDays;
        }
        if (indicativeSlaDaysMax != null) return indicativeSlaDaysMax;
        return indicativeSlaDaysMin;
    }

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
