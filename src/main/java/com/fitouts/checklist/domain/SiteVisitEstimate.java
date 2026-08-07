package com.fitouts.checklist.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitouts.company.domain.Company;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "site_visit_estimates")
@Getter
@Setter
public class SiteVisitEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_visit_uuid", nullable = false, unique = true)
    private SiteVisit siteVisit;

    @Column(name = "quote_no", length = 64)
    private String quoteNo;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(nullable = false, length = 16)
    private String revision = "R0";

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(name = "client_address", length = 500)
    private String clientAddress;

    @Column(name = "project_label", length = 200)
    private String projectLabel;

    @Column(name = "location_label", length = 200)
    private String locationLabel;

    @Column(length = 500)
    private String subject;

    @Column(name = "prepared_by", length = 200)
    private String preparedBy;

    @Column(nullable = false, length = 8)
    private String currency = "AED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SiteVisitEstimateStatus status = SiteVisitEstimateStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<SiteVisitEstimateLine> lines = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public void clearAndAddLines(List<SiteVisitEstimateLine> nextLines) {
        lines.clear();
        if (nextLines == null) {
            return;
        }
        for (SiteVisitEstimateLine line : nextLines) {
            addLine(line);
        }
    }

    public void addLine(SiteVisitEstimateLine line) {
        lines.add(line);
        line.setEstimate(this);
    }

    @PrePersist
    void createTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = OffsetDateTime.now();
    }
}
