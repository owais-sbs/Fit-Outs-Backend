package com.fitouts.approval.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One submission attempt. Resubmissions increment the version rather than overwrite. */
@Entity
@Table(name = "case_submission")
@Getter
@Setter
public class CaseSubmission {

    @Id
    private UUID uuid;

    @Column(name = "case_uuid", nullable = false)
    private UUID caseUuid;

    @Column(nullable = false)
    private int version;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_date", nullable = false)
    private LocalDate submittedDate;

    @Column(length = 120)
    private String channel;

    @Column(name = "receipt_file_path", columnDefinition = "TEXT")
    private String receiptFilePath;

    @Column(name = "fee_amount")
    private BigDecimal feeAmount;

    @Column(nullable = false, length = 24)
    private String outcome = "PENDING";

    @Column(name = "outcome_date")
    private LocalDate outcomeDate;

    /** Actual working days taken, recorded on outcome and fed back into the authority median. */
    @Column(name = "turnaround_days")
    private Integer turnaroundDays;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
