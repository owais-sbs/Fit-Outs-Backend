package com.fitouts.procurement.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockIssueRequest {
    private UUID materialId;
    private BigDecimal quantity;
    private Long projectId;
    private String referenceNo;
    private String notes;
    private LocalDateTime movementDate;
}
