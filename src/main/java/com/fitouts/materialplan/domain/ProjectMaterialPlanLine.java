package com.fitouts.materialplan.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_material_plan_line")
@Getter
@Setter
public class ProjectMaterialPlanLine {

    @Id
    private UUID uuid;

    @Column(name = "plan_uuid", nullable = false)
    private UUID planUuid;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    @Column(name = "planned_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal plannedQty = BigDecimal.ZERO;

    @Column(name = "stock_qty_snapshot", nullable = false, precision = 14, scale = 4)
    private BigDecimal stockQtySnapshot = BigDecimal.ZERO;

    @Column(length = 32)
    private String unit;

    @Column(name = "shortage_flag", nullable = false)
    private boolean shortageFlag;

    @Column(name = "reserved_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal reservedQty = BigDecimal.ZERO;

    private String notes;

    @Column(name = "substitute_reason", length = 255)
    private String substituteReason;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (plannedQty == null) plannedQty = BigDecimal.ZERO;
        if (stockQtySnapshot == null) stockQtySnapshot = BigDecimal.ZERO;
        if (reservedQty == null) reservedQty = BigDecimal.ZERO;
    }
}
