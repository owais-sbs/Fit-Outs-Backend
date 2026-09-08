package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tender_packages")
@Getter
@Setter
public class Package implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "package_ref", nullable = false)
    private String packageRef;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "scope", columnDefinition = "text")
    private String scope;

    @Column(name = "boq_document_id")
    private UUID boqDocumentId;

    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "tender_package_boq_lines", joinColumns = @jakarta.persistence.JoinColumn(name = "package_id"))
    @Column(name = "boq_line_id")
    private java.util.List<UUID> boqLineIds = new java.util.ArrayList<>();

    @Column(name = "trade")
    private String trade;

    @Column(name = "drawing_revision")
    private String drawingRevision;

    @Column(name = "specification_summary", columnDefinition = "text")
    private String specificationSummary;

    @Column(name = "site_visit_info", columnDefinition = "text")
    private String siteVisitInfo;

    @Column(name = "tender_deadline")
    private OffsetDateTime tenderDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PackageStatus status = PackageStatus.DRAFT;

    @Column(name = "bids_opened", nullable = false)
    private Boolean bidsOpened = false;

    @Column(name = "bids_opened_at")
    private OffsetDateTime bidsOpenedAt;

    @Column(name = "bids_opened_by")
    private Long bidsOpenedBy;

    // --- Eligibility Gating Rules ---
    @Column(name = "require_car_insurance", nullable = false)
    private Boolean requireCarInsurance = true;

    @Column(name = "require_tpl_insurance", nullable = false)
    private Boolean requireTplInsurance = true;

    @Column(name = "require_workmen_comp", nullable = false)
    private Boolean requireWorkmenComp = true;

    @Column(name = "require_civil_defence", nullable = false)
    private Boolean requireCivilDefence = false;

    @Column(name = "require_sira_licence", nullable = false)
    private Boolean requireSiraLicence = false;

    @Column(name = "require_electrical_cert", nullable = false)
    private Boolean requireElectricalCert = false;

    @Column(name = "required_community_reg")
    private String requiredCommunityReg;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
