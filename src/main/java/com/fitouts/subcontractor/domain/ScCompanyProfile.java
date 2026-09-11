package com.fitouts.subcontractor.domain;

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
@Table(name = "sc_company_profile")
@Getter
@Setter
public class ScCompanyProfile {

    @Id
    private UUID uuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "admin_account_id", nullable = false)
    private Long adminAccountId;

    @Column(name = "organization_uuid")
    private UUID organizationUuid;

    @Column(name = "legal_company_name")
    private String legalCompanyName;

    @Column(name = "trade_licence_number", length = 120)
    private String tradeLicenceNumber;

    @Column(name = "trade_licence_file_path", columnDefinition = "TEXT")
    private String tradeLicenceFilePath;

    @Column(name = "trade_licence_expiry")
    private LocalDate tradeLicenceExpiry;

    @Column(length = 64)
    private String trn;

    @Column(name = "registered_address", columnDefinition = "TEXT")
    private String registeredAddress;

    @Column(name = "primary_contact_name", length = 160)
    private String primaryContactName;

    @Column(name = "primary_contact_email")
    private String primaryContactEmail;

    @Column(name = "primary_contact_phone", length = 64)
    private String primaryContactPhone;

    @Column(name = "accounts_contact_name", length = 160)
    private String accountsContactName;

    @Column(name = "accounts_contact_email")
    private String accountsContactEmail;

    @Column(name = "accounts_contact_phone", length = 64)
    private String accountsContactPhone;

    @Column(name = "trade_categories", columnDefinition = "TEXT")
    private String tradeCategories;

    @Column(name = "declared_capacity")
    private String declaredCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScCompanyStatus status = ScCompanyStatus.INVITED;

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
