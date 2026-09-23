package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class ScMeasureClaimLineRequest {
    private UUID claimLineUuid;
    private BigDecimal measuredQty;
    private String qsComment;
}
