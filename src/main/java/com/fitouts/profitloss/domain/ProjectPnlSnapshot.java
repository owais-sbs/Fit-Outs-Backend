package com.fitouts.profitloss.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_pnl_snapshot")
@Getter
@Setter
public class ProjectPnlSnapshot {

    @Id
    private UUID uuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "period_year_month", nullable = false, length = 7)
    private String periodYearMonth;

    @Column(name = "contract_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal contractValue = BigDecimal.ZERO;

    @Column(name = "material_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal materialCost = BigDecimal.ZERO;

    @Column(name = "labour_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal labourCost = BigDecimal.ZERO;

    @Column(name = "sc_certified_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal scCertifiedCost = BigDecimal.ZERO;

    @Column(name = "variation_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal variationCost = BigDecimal.ZERO;

    @Column(name = "overhead_allocated", nullable = false, precision = 14, scale = 2)
    private BigDecimal overheadAllocated = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal margin = BigDecimal.ZERO;

    @Column(name = "original_contract_value", precision = 14, scale = 2)
    private BigDecimal originalContractValue;

    @Column(name = "original_estimated_cost", precision = 14, scale = 2)
    private BigDecimal originalEstimatedCost;

    @Column(name = "margin_vs_original_estimate", precision = 14, scale = 2)
    private BigDecimal marginVsOriginalEstimate;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (calculatedAt == null) {
            calculatedAt = OffsetDateTime.now();
        }
    }
}
