package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
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
@Table(name = "sc_organization")
@Getter
@Setter
public class ScOrganization {

    @Id
    private UUID uuid;

    @Column(name = "legal_company_name")
    private String legalCompanyName;

    @Column(name = "trade_licence_number", length = 120)
    private String tradeLicenceNumber;

    @Column(name = "trade_licence_authority", length = 160)
    private String tradeLicenceAuthority;

    @Column(name = "trade_licence_activities", columnDefinition = "TEXT")
    private String tradeLicenceActivities;

    @Column(name = "trade_licence_file_path", columnDefinition = "TEXT")
    private String tradeLicenceFilePath;

    @Column(name = "trade_licence_expiry")
    private java.time.LocalDate tradeLicenceExpiry;

    @Column(name = "establishment_card_file_path", columnDefinition = "TEXT")
    private String establishmentCardFilePath;

    @Column(name = "establishment_card_expiry")
    private java.time.LocalDate establishmentCardExpiry;

    @Column(length = 64)
    private String trn;

    @Column(name = "vat_certificate_file_path", columnDefinition = "TEXT")
    private String vatCertificateFilePath;

    @Column(name = "registered_address", columnDefinition = "TEXT")
    private String registeredAddress;

    @Column(name = "location_pin", length = 120)
    private String locationPin;

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

    @Column(name = "monthly_capacity_value", precision = 14, scale = 2)
    private BigDecimal monthlyCapacityValue;

    @Column(name = "monthly_capacity_manpower")
    private Integer monthlyCapacityManpower;

    @Column(name = "years_in_operation")
    private Integer yearsInOperation;

    @Column(name = "annual_turnover_band", length = 64)
    private String annualTurnoverBand;

    @Column(name = "workshop_address", columnDefinition = "TEXT")
    private String workshopAddress;

    @Column(name = "workshop_photos", columnDefinition = "TEXT")
    private String workshopPhotos;

    @Column(name = "hse_policy_file_path", columnDefinition = "TEXT")
    private String hsePolicyFilePath;

    @Column(name = "hse_lti_count")
    private Integer hseLtiCount;

    @Column(name = "hse_officer_name", length = 160)
    private String hseOfficerName;

    @Column(name = "hse_officer_cert_file_path", columnDefinition = "TEXT")
    private String hseOfficerCertFilePath;

    @Column(name = "hse_officer_cert_expiry")
    private java.time.LocalDate hseOfficerCertExpiry;

    @Column(name = "payment_terms_accepted", length = 64)
    private String paymentTermsAccepted;

    @Column(name = "retention_pct_accepted", precision = 5, scale = 2)
    private BigDecimal retentionPctAccepted;

    @Column(name = "advance_payment_required", precision = 14, scale = 2)
    private BigDecimal advancePaymentRequired;

    @Column(name = "workforce_total")
    private Integer workforceTotal;

    @Column(name = "workforce_trade_breakdown", columnDefinition = "TEXT")
    private String workforceTradeBreakdown;

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    @Column(name = "is_cross_tenant_visible", nullable = false)
    private boolean crossTenantVisible;

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
