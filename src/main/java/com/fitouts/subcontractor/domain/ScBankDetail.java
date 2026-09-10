package com.fitouts.subcontractor.domain;

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
@Table(name = "sc_bank_detail")
@Getter
@Setter
public class ScBankDetail {

    @Id
    private UUID uuid;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(name = "bank_name", length = 160)
    private String bankName;

    @Column(name = "account_name", length = 160)
    private String accountName;

    @Column(length = 64)
    private String iban;

    @Column(name = "swift_code", length = 32)
    private String swiftCode;

    @Column(name = "bank_letter_file_path", columnDefinition = "TEXT")
    private String bankLetterFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 24)
    private ScBankVerificationStatus verificationStatus = ScBankVerificationStatus.PENDING;

    @Column(name = "verified_by_account_id")
    private Long verifiedByAccountId;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
