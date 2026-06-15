package com.fitouts.workitemconfiguration.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.company.domain.Company;
import com.fitouts.shared.enums.QuantityFormulaType;
import com.fitouts.shared.enums.UnitType;
import com.fitouts.shared.enums.WorkItemCategory;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "work_items", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "work_item_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "work_item_name", nullable = false, length = 200)
    private String workItemName;

    @Column(name = "work_item_code", nullable = false, length = 50)
    private String workItemCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WorkItemCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "ceiling_applicable", nullable = false)
    @Builder.Default
    private Boolean ceilingApplicable = false;

    @Column(name = "wall_applicable", nullable = false)
    @Builder.Default
    private Boolean wallApplicable = false;

    @Column(name = "floor_applicable", nullable = false)
    @Builder.Default
    private Boolean floorApplicable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20)
    private UnitType unitType;

    @Column(name = "default_rate", precision = 12, scale = 2)
    private BigDecimal defaultRate;

    @Column(name = "subcontractor_rate", precision = 12, scale = 2)
    private BigDecimal subcontractorRate;

    @Column(name = "markup_percentage", precision = 5, scale = 2)
    private BigDecimal markupPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_formula_type", nullable = false, length = 30)
    @Builder.Default
    private QuantityFormulaType quantityFormulaType = QuantityFormulaType.MANUAL;

    @Column(length = 50)
    private String icon;

    @Column(name = "color_tag", length = 20)
    private String colorTag;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
