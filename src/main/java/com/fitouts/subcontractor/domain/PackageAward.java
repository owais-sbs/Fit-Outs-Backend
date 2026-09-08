package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "package_awards")
@Getter
@Setter
public class PackageAward implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "winning_quote_id")
    private UUID winningQuoteId;

    @Column(name = "agreed_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal agreedAmount;

    @Column(name = "awarded_at", nullable = false)
    private OffsetDateTime awardedAt;

    @Column(name = "awarded_by")
    private Long awardedBy;

    @Column(name = "scope", columnDefinition = "text")
    private String scope;

    @Column(name = "priced_boq_document_id")
    private UUID pricedBoqDocumentId;

    @Column(name = "drawing_revision")
    private String drawingRevision;

    @Column(name = "programme_extract", columnDefinition = "text")
    private String programmeExtract;

    @Column(name = "site_rules", columnDefinition = "text")
    private String siteRules;

    @Column(name = "status", nullable = false)
    private String status = "ISSUED"; // ISSUED, ACCEPTED, DECLINED

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (awardedAt == null) {
            awardedAt = OffsetDateTime.now();
        }
    }
}
