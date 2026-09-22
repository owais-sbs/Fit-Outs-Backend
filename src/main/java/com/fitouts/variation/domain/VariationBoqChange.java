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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "variation_boq_change")
@Getter
@Setter
public class VariationBoqChange {
    @Id
    private UUID uuid;
    @Column(name = "variation_uuid", nullable = false)
    private UUID variationUuid;
    @Column(name = "variation_line_uuid")
    private UUID variationLineUuid;
    @Column(name = "source_boq_uuid", nullable = false)
    private UUID sourceBoqUuid;
    @Column(name = "resulting_boq_uuid", nullable = false)
    private UUID resultingBoqUuid;
    @Column(name = "source_boq_line_id")
    private UUID sourceBoqLineId;
    @Column(name = "resulting_boq_line_id")
    private UUID resultingBoqLineId;
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private VariationLineType changeType;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(length = 20)
    private String unit;
    @Column(name = "previous_quantity", precision = 14, scale = 4)
    private BigDecimal previousQuantity;
    @Column(name = "new_quantity", precision = 14, scale = 4)
    private BigDecimal newQuantity;
    @Column(name = "previous_rate", precision = 12, scale = 2)
    private BigDecimal previousRate;
    @Column(name = "new_rate", precision = 12, scale = 2)
    private BigDecimal newRate;
    @Column(name = "previous_amount", precision = 14, scale = 2)
    private BigDecimal previousAmount;
    @Column(name = "new_amount", precision = 14, scale = 2)
    private BigDecimal newAmount;
    @Column(name = "delta_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal deltaAmount;
    @Column(name = "applied_by")
    private Long appliedBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
