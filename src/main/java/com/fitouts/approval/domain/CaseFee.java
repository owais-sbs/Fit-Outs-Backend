package com.fitouts.approval.domain;

import java.math.BigDecimal;
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

/**
 * A fee, deposit, fine or knowledge fee on a case.
 *
 * <p>A refundable deposit is a receivable. It stays outstanding on the ledger from the day it
 * is paid until refund_received_date is filled in, which is the whole point: these are the
 * amounts that quietly never come back.
 */
@Entity
@Table(name = "case_fee")
@Getter
@Setter
public class CaseFee {

    @Id
    private UUID uuid;

    @Column(name = "case_uuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 24)
    private String type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "AED";

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_ref", length = 120)
    private String paymentRef;

    @Column(name = "receipt_file_path", columnDefinition = "TEXT")
    private String receiptFilePath;

    @Column(name = "cost_code", length = 64)
    private String costCode;

    @Column(name = "is_refundable", nullable = false)
    private boolean refundable = false;

    @Column(name = "refund_claimed_date")
    private LocalDate refundClaimedDate;

    @Column(name = "refund_received_date")
    private LocalDate refundReceivedDate;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Money paid out that a third party still holds. */
    public boolean isOutstandingDeposit() {
        return refundable && paidDate != null && refundReceivedDate == null;
    }

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
