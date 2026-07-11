package com.fitouts.workitemconfiguration.api;

import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;

import com.fitouts.shared.enums.QuantityFormulaType;
import com.fitouts.shared.enums.UnitType;
import com.fitouts.shared.enums.WorkItemCategory;

// import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemUpdateRequest {

    // @Size(max = 200, message = "Work item name must not exceed 200 characters")
    private String workItemName;

    // @Size(max = 50, message = "Work item code must not exceed 50 characters")
    private String workItemCode;

    private WorkItemCategory category;

    private UUID workItemMasterId;

    // @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private Boolean ceilingApplicable;

    private Boolean wallApplicable;

    private Boolean floorApplicable;

    private UnitType unitType;

    private BigDecimal defaultRate;

    private BigDecimal subcontractorRate;

    private BigDecimal markupPercentage;

    private BigDecimal costPrice;

    private Boolean sellingPriceOverride;

    private Boolean costPriceOverride;

    private List<WorkItemMaterialLineRequest> materialLines;

    private QuantityFormulaType quantityFormulaType;

    // @Size(max = 50, message = "Icon must not exceed 50 characters")
    private String icon;

    // @Size(max = 20, message = "Color tag must not exceed 20 characters")
    private String colorTag;
}
