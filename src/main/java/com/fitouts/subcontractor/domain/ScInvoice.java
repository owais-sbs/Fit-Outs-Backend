package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
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
@Table(name = "sc_invoice")
@Getter
@Setter
public class ScInvoice {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "claim_uuid")
    private UUID claimUuid;

    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "AED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScInvoiceStatus status = ScInvoiceStatus.DRAFT;

    @Column(name = "attachment_paths")
    private String attachmentPaths;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column
    private String reason;

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
