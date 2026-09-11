package com.fitouts.subcontractor.domain;

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
@Table(name = "subcontractor_package")
@Getter
@Setter
public class SubcontractorPackage {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "boq_section_code")
    private String boqSectionCode;

    /** When set, this package maps to a single BOQ line for per-item subcontractor assignment. */
    @Column(name = "boq_line_id")
    private UUID boqLineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubcontractorPackageStatus status = SubcontractorPackageStatus.OPEN;

    @Column(name = "appointed_account_id")
    private Long appointedAccountId;

    @Column(name = "appointed_company_name")
    private String appointedCompanyName;

    // --- Populated by the schedule apply cascade (Module 43) ----------------

    /** Links the shell back to the template trade it came from, e.g. TP-MEP. */
    @Column(name = "trade_package_code", length = 32)
    private String tradePackageCode;

    /** Earliest start across the trade's activities. Moves when the programme moves. */
    @Column(name = "planned_start")
    private java.time.LocalDate plannedStart;

    @Column(name = "planned_finish")
    private java.time.LocalDate plannedFinish;

    /** Comma-separated activity codes covered by this package. */
    @Column(name = "activity_codes", columnDefinition = "text")
    private String activityCodes;

    // --- Wave 6 tender fields ------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "tender_status", length = 32)
    private ScTenderStatus tenderStatus;

    @Column(name = "tender_deadline")
    private OffsetDateTime tenderDeadline;

    @Column(name = "tender_issued_at")
    private OffsetDateTime tenderIssuedAt;

    @Column(name = "quote_validity_days")
    private Integer quoteValidityDays;

    @Column(name = "payment_terms", length = 128)
    private String paymentTerms;

    @Column(name = "retention_pct", precision = 5, scale = 2)
    private java.math.BigDecimal retentionPct;

    @Column(name = "site_visit_at")
    private OffsetDateTime siteVisitAt;

    @Column(name = "tender_description", columnDefinition = "TEXT")
    private String tenderDescription;

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
