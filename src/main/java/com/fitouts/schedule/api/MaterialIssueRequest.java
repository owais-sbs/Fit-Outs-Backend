package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class MaterialIssueRequest {
    private UUID materialId;
    private BigDecimal qty;
    private UUID planLineUuid;
}
