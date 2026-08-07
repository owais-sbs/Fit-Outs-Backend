package com.fitouts.checklist.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SiteVisitEstimateLineRequest {

    private UUID workItemId;
    private UUID roomTypeId;
    private String floorName;
    private String roomName;
    private String category;
    private String description;
    private BigDecimal qty;
    private String unit;
    private BigDecimal rate;
    private Integer displayOrder;
}
