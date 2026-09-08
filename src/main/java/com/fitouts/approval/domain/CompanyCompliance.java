package com.fitouts.approval.domain;

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
 * Company-level compliance document (licence, insurance, specialist approval).
 * Pack assembly auto-collects from here, and the company gate reads it before submission.
 */
@Entity
@Table(name = "company_compliance")
@Getter
@Setter
public class CompanyCompliance {

    @Id
    private UUID uuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "document_type_code", nullable = false, length = 32)
    private String documentTypeCode;

    @Column(name = "reference_no", length = 120)
    private String referenceNo;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(nullable = false, length = 24)
    private String status = "MISSING";

    @Column(name = "renewal_owner_account_id")
    private Long renewalOwnerAccountId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Recomputes status from the file and expiry date. */
    public void refreshStatus() {
        if ("NOT_APPLICABLE".equals(status)) {
            return;
        }
        if (filePath == null || filePath.isBlank()) {
            status = "MISSING";
        } else if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            status = "EXPIRED";
        } else {
            status = "ATTACHED";
        }
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
