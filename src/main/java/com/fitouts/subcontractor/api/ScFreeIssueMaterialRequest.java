package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitouts.subcontractor.domain.ScFreeIssueSuppliedBy;

import lombok.Data;

@Data
public class ScFreeIssueMaterialRequest {
    private UUID uuid;
    private String itemDescription;
    private ScFreeIssueSuppliedBy suppliedBy;
    private BigDecimal quantity;
    private String unit;
    private String notes;
}
