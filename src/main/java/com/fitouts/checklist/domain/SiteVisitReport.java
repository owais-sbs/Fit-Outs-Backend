package com.fitouts.checklist.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "site_visit_reports")
@Getter
@Setter
public class SiteVisitReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_visit_uuid", nullable = false, unique = true)
    private SiteVisit siteVisit;

    @Column(nullable = false)
    private String outcome;

    private String notes;

    private Long submittedBy;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SiteVisitReportItem> items = new ArrayList<>();

    public void addItem(SiteVisitReportItem item) {
        items.add(item);
        item.setReport(this);
    }

    @PrePersist
    void setSubmittedAt() {
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
    }
}
