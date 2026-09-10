package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "sc_site_report")
@Getter
@Setter
public class ScSiteReport {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid")
    private UUID packageUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ScSiteReportType reportType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "delay_reason_code", length = 64)
    private String delayReasonCode;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "delivered_date")
    private LocalDate deliveredDate;

    @Column(name = "material_name")
    private String materialName;

    private BigDecimal quantity;

    @Column(length = 32)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScSiteReportStatus status = ScSiteReportStatus.OPEN;

    @Column(name = "attachment_paths")
    private String attachmentPaths;

    @Column(name = "raised_by")
    private Long raisedBy;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

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
