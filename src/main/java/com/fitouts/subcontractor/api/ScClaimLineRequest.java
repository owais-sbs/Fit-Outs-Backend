package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class ScClaimLineRequest {
    private UUID awardBoqLineUuid;
    private BigDecimal claimedQty;
}
