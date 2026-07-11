package com.fitouts.procurement.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.StockMovementType;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {
    private UUID id;
    private UUID materialId;
    private String materialName;
    private String materialCode;
    private StockMovementType movementType;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private Long projectId;
    private String projectName;
    private String referenceNo;
    private String notes;
    private LocalDateTime movementDate;
    private LocalDateTime createdAt;
}
