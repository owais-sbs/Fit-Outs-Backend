package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
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
@Table(name = "sc_package_free_issue_material")
@Getter
@Setter
public class ScFreeIssueMaterial {
    @Id
    private UUID uuid;
    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "item_description", nullable = false, length = 240)
    private String itemDescription;
    @Enumerated(EnumType.STRING)
    @Column(name = "supplied_by", nullable = false, length = 32)
    private ScFreeIssueSuppliedBy suppliedBy;
    @Column(precision = 14, scale = 4)
    private BigDecimal quantity;
    @Column(length = 32)
    private String unit;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    @Column(name = "created_at", nullable = false, updatable = false)
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
