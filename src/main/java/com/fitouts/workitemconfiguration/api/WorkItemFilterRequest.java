package com.fitouts.workitemconfiguration.api;

import com.fitouts.shared.enums.UnitType;
import com.fitouts.shared.enums.WorkItemCategory;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemFilterRequest {

    private String search;
    private WorkItemCategory category;
    private Boolean active;
    private Boolean ceilingApplicable;
    private Boolean wallApplicable;
    private Boolean floorApplicable;
    private UnitType unitType;
}
