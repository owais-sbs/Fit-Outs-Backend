package com.fitouts.workitemconfiguration.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemMaterialLineRequest {
    private UUID materialId;
    private BigDecimal quantityPerUnit;
    private BigDecimal wastagePercent;
}
