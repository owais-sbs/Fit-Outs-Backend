package com.fitouts.workitemconfiguration.api;

import java.math.BigDecimal;

import com.fitouts.shared.enums.QuantityFormulaType;
import com.fitouts.shared.enums.UnitType;
import com.fitouts.shared.enums.WorkItemCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemCreateRequest {

    @NotBlank(message = "Work item name is required")
    @Size(max = 200, message = "Work item name must not exceed 200 characters")
    private String workItemName;

    @NotBlank(message = "Work item code is required")
    @Size(max = 50, message = "Work item code must not exceed 50 characters")
    private String workItemCode;

    @NotNull(message = "Category is required")
    private WorkItemCategory category;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Builder.Default
    private Boolean ceilingApplicable = false;

    @Builder.Default
    private Boolean wallApplicable = false;

    @Builder.Default
    private Boolean floorApplicable = false;

    @NotNull(message = "Unit type is required")
    private UnitType unitType;

    private BigDecimal defaultRate;

    private BigDecimal subcontractorRate;

    private BigDecimal markupPercentage;

    @Builder.Default
    private QuantityFormulaType quantityFormulaType = QuantityFormulaType.MANUAL;

    @Size(max = 50, message = "Icon must not exceed 50 characters")
    private String icon;

    @Size(max = 20, message = "Color tag must not exceed 20 characters")
    private String colorTag;
}
