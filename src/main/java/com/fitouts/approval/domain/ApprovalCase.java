package com.fitouts.approval.domain;

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
@Table(name = "approval_case")
@Getter
@Setter
public class ApprovalCase {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "permit_type_code", nullable = false, length = 32)
    private String permitTypeCode;

    @Column(name = "permit_type_name", nullable = false)
    private String permitTypeName;

    @Column(name = "authority_code", length = 32)
    private String authorityCode;

    @Column(name = "authority_name")
    private String authorityName;

    @Column(name = "case_number", nullable = false, length = 64)
    private String caseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalCaseStatus status = ApprovalCaseStatus.NOT_STARTED;

    @Column(name = "assigned_to_account_id")
    private Long assignedToAccountId;

    @Column(name = "target_submission_date")
    private LocalDate targetSubmissionDate;

    @Column(name = "submitted_date")
    private LocalDate submittedDate;

    @Column(name = "authority_reference", length = 120)
    private String authorityReference;

    @Column(name = "sla_due_date")
    private LocalDate slaDueDate;

    @Column(name = "sla_days")
    private Integer slaDays;

    @Column(name = "approved_date")
    private LocalDate approvedDate;

    @Column(name = "permit_number", length = 120)
    private String permitNumber;

    @Column(name = "permit_file_path", columnDefinition = "TEXT")
    private String permitFilePath;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "fee_paid", nullable = false)
    private BigDecimal feePaid = BigDecimal.ZERO;

    @Column(name = "deposit_paid", nullable = false)
    private BigDecimal depositPaid = BigDecimal.ZERO;

    @Column(name = "deposit_status", length = 24)
    private String depositStatus;

    @Column(name = "deposit_refund_case_uuid")
    private UUID depositRefundCaseUuid;

    @Column(name = "renewal_of_case_uuid")
    private UUID renewalOfCaseUuid;

    @Column(name = "prerequisite_permit_codes", columnDefinition = "TEXT")
    private String prerequisitePermitCodes;

    @Column(name = "blocks_activity_codes", columnDefinition = "TEXT")
    private String blocksActivityCodes;

    @Column(name = "linked_activity_uuids", columnDefinition = "TEXT")
    private String linkedActivityUuids;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 0;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    @Column(name = "closed_reason")
    private String closedReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Days until the SLA falls due; negative once the authority is late. */
    public Long daysToSlaDue() {
        if (slaDueDate == null || !status.isAwaitingAuthority()) return null;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), slaDueDate);
    }

    /** Days until the permit expires; negative once it has lapsed. */
    public Long daysToExpiry() {
        if (expiryDate == null) return null;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
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
