package com.fitouts.completion.domain;

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

@Entity
@Table(name = "project_closeout_checklist")
@Getter
@Setter
public class ProjectCloseoutChecklist {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "final_invoice_confirmed_at")
    private OffsetDateTime finalInvoiceConfirmedAt;

    @Column(name = "final_invoice_confirmed_by")
    private Long finalInvoiceConfirmedBy;

    @Column(name = "accounting_synced_at")
    private OffsetDateTime accountingSyncedAt;

    @Column(name = "accounting_synced_by")
    private Long accountingSyncedBy;

    @Column(name = "commercially_closed_at")
    private OffsetDateTime commerciallyClosedAt;

    @Column(name = "commercially_closed_by")
    private Long commerciallyClosedBy;

    @Column(name = "dlp_start_date")
    private LocalDate dlpStartDate;

    @Column(name = "dlp_end_date")
    private LocalDate dlpEndDate;

    @Column(name = "dlp_duration_months")
    private Integer dlpDurationMonths;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "archived_by")
    private Long archivedBy;

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

    public boolean isFinalInvoiceConfirmed() {
        return finalInvoiceConfirmedAt != null;
    }

    public boolean isAccountingSynced() {
        return accountingSyncedAt != null;
    }

    public boolean isCommerciallyClosed() {
        return commerciallyClosedAt != null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
