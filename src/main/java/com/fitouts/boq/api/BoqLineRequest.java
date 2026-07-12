package com.fitouts.boq.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqLineRequest {
    private UUID id;
    private String categoryCode;
    private String categoryName;
    private String description;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal rate;
    private UUID qtoLineId;
    private String floorLabel;
    private String roomLabel;
    private Integer sortOrder;
    private String source;
}
