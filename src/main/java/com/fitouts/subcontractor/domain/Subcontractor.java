package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "subcontractors")
@Getter
@Setter
public class Subcontractor implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId; // Maps to tenant_id (SaaS Tenant Company UUID)

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "licence_no")
    private String licenceNo;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Column(name = "trn")
    private String trn;

    @Column(name = "establishment_card_expiry")
    private LocalDate establishmentCardExpiry;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "location_pin")
    private String locationPin;

    @Column(name = "trades", columnDefinition = "text")
    private String trades;

    @Column(name = "capacity_band")
    private String capacityBand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubcontractorStatus status = SubcontractorStatus.INVITED;

    @Column(name = "approved_trades", columnDefinition = "text")
    private String approvedTrades;

    @Column(name = "max_package_value", precision = 14, scale = 2)
    private BigDecimal maxPackageValue;

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    @Column(name = "is_cross_tenant_visible", nullable = false)
    private Boolean isCrossTenantVisible = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
