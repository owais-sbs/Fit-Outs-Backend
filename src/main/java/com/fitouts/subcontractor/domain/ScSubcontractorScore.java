package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sc_subcontractor_score")
@Getter
@Setter
public class ScSubcontractorScore {

    @Id
    private UUID uuid;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(name = "package_uuid")
    private UUID packageUuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "programme_score", precision = 5, scale = 2)
    private BigDecimal programmeScore;

    @Column(name = "safety_score", precision = 5, scale = 2)
    private BigDecimal safetyScore;

    @Column(name = "commercial_score", precision = 5, scale = 2)
    private BigDecimal commercialScore;

    @Column(name = "responsiveness_score", precision = 5, scale = 2)
    private BigDecimal responsivenessScore;

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (computedAt == null) {
            computedAt = now;
        }
        createdAt = now;
    }
}
