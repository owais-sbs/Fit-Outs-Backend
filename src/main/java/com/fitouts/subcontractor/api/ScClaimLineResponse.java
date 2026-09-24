package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScClaimLineResponse {
    private UUID uuid;
    private UUID claimUuid;
    private UUID awardBoqLineUuid;
    private UUID boqLineId;
    private String sectionCode;
    private String description;
    private String unit;
    private BigDecimal contractQty;
    private BigDecimal awardRate;
    private BigDecimal previousCertifiedQty;
    private BigDecimal claimedQty;
    private BigDecimal claimedValue;
    private BigDecimal measuredQty;
    private BigDecimal measuredValue;
    private BigDecimal certifiedQty;
    private BigDecimal certifiedValue;
    private String qsComment;
    private Integer sortOrder;
}
