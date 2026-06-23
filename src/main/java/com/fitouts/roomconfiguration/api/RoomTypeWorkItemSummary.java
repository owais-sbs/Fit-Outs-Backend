package com.fitouts.roomconfiguration.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitouts.shared.enums.QuantityFormulaType;
import com.fitouts.shared.enums.UnitType;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeWorkItemSummary {

    private UUID id;
    private String workItemName;
    private String workItemCode;
    private UUID workItemMasterId;
    private String workItemMasterName;
    private UnitType unitType;
    private BigDecimal defaultRate;
    private BigDecimal subcontractorRate;
    private QuantityFormulaType quantityFormulaType;
    private Boolean ceilingApplicable;
    private Boolean wallApplicable;
    private Boolean floorApplicable;
}
