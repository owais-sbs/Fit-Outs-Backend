package com.fitouts.variation.domain;

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
@Table(name = "project_commercial")
@Getter
@Setter
public class ProjectCommercial {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "original_contract_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalContractValue = BigDecimal.ZERO;

    @Column(name = "current_contract_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentContractValue = BigDecimal.ZERO;

    @Column(name = "original_cost", precision = 14, scale = 2)
    private BigDecimal originalCost;

    @Column(name = "current_cost", precision = 14, scale = 2)
    private BigDecimal currentCost;

    @Column(name = "current_margin", precision = 14, scale = 2)
    private BigDecimal currentMargin;

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
