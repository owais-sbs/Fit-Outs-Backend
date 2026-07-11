package com.fitouts.workitemconfiguration.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitouts.procurement.domain.Material;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "work_item_materials", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"work_item_id", "material_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_item_id", nullable = false)
    private WorkItem workItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity_per_unit", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal quantityPerUnit = BigDecimal.ONE;

    @Column(name = "wastage_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal wastagePercent = BigDecimal.ZERO;
}
