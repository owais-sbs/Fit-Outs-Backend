package com.fitouts.schedule.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A physical or statutory hold the programme cannot compress: screed curing, waterproofing
 * flood tests, concrete strength gain, statutory notice periods.
 */
@Entity
@Table(name = "locked_constraint_rule")
@Getter
@Setter
public class LockedConstraintRule {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "minimum_hold_raw", length = 120)
    private String minimumHoldRaw;

    @Column(name = "minimum_hold_working_days")
    private Integer minimumHoldWorkingDays;

    @Column(name = "applies_after", columnDefinition = "text")
    private String appliesAfter;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "engine_behaviour", columnDefinition = "text")
    private String engineBehaviour;

    @Column(name = "is_compressible", nullable = false)
    private boolean compressible;

    @Column(name = "match_keywords", columnDefinition = "text")
    private String matchKeywords;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
