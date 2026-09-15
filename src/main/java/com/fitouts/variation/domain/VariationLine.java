package com.fitouts.variation.domain;

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
@Table(name = "variation_line")
@Getter
@Setter
public class VariationLine {

    @Id
    private UUID uuid;

    @Column(name = "variation_uuid", nullable = false)
    private UUID variationUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private VariationLineType lineType;

    @Column(name = "source_boq_line_id")
    private UUID sourceBoqLineId;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 32)
    private String unit;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "sell_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellRate = BigDecimal.ZERO;

    @Column(name = "sell_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellAmount = BigDecimal.ZERO;

    @Column(name = "cost_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal costRate = BigDecimal.ZERO;

    @Column(name = "cost_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
