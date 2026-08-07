package com.fitouts.checklist.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SiteVisitEstimateLineResponse {

    private UUID uuid;
    private UUID workItemId;
    private UUID roomTypeId;
    private String floorName;
    private String roomName;
    private String category;
    private String description;
    private BigDecimal qty;
    private String unit;
    private BigDecimal rate;
    private BigDecimal amount;
    private Integer displayOrder;
}
