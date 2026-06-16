package com.fitouts.workitemconfiguration.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.QuantityFormulaType;
import com.fitouts.shared.enums.UnitType;
import com.fitouts.shared.enums.WorkItemCategory;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemResponse {

    private UUID id;
    private UUID companyId;
    private String workItemName;
    private String workItemCode;
    private WorkItemCategory category;
    private UUID workItemMasterId;
    private String workItemMasterName;
    private String description;
    private Boolean ceilingApplicable;
    private Boolean wallApplicable;
    private Boolean floorApplicable;
    private UnitType unitType;
    private BigDecimal defaultRate;
    private BigDecimal subcontractorRate;
    private BigDecimal markupPercentage;
    private QuantityFormulaType quantityFormulaType;
    private String icon;
    private String colorTag;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
}
