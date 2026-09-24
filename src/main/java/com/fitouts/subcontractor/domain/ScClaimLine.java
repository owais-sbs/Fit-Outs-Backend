package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
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

@Entity
@Table(name = "sc_claim_line")
@Getter
@Setter
public class ScClaimLine {

    @Id
    private UUID uuid;

    @Column(name = "claim_uuid", nullable = false)
    private UUID claimUuid;

    @Column(name = "award_boq_line_uuid")
    private UUID awardBoqLineUuid;

    @Column(name = "boq_line_id")
    private UUID boqLineId;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "section_code", length = 64)
    private String sectionCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 32)
    private String unit;

    @Column(name = "contract_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal contractQty = BigDecimal.ZERO;

    @Column(name = "award_rate", nullable = false, precision = 18, scale = 4)
    private BigDecimal awardRate = BigDecimal.ZERO;

    @Column(name = "previous_certified_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal previousCertifiedQty = BigDecimal.ZERO;

    @Column(name = "claimed_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal claimedQty = BigDecimal.ZERO;

    @Column(name = "claimed_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal claimedValue = BigDecimal.ZERO;

    @Column(name = "measured_qty", precision = 18, scale = 4)
    private BigDecimal measuredQty;

    @Column(name = "measured_value", precision = 18, scale = 2)
    private BigDecimal measuredValue;

    @Column(name = "certified_qty", precision = 18, scale = 4)
    private BigDecimal certifiedQty;

    @Column(name = "certified_value", precision = 18, scale = 2)
    private BigDecimal certifiedValue;

    @Column(name = "qs_comment", columnDefinition = "TEXT")
    private String qsComment;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

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
