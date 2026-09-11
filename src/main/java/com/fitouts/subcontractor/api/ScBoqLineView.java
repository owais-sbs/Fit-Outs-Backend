package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScBoqLineView {
    private UUID boqLineId;
    private UUID packageUuid;
    private Long projectId;
    private String projectName;
    private String packageName;
    private String sectionCode;
    private String description;
    private String unit;
    private String roomLabel;
    private String floorLabel;
    private BigDecimal plannedQty;
    private BigDecimal approvedQty;
    private BigDecimal remainingQty;
    private BigDecimal rate;
    private BigDecimal amount;
}
