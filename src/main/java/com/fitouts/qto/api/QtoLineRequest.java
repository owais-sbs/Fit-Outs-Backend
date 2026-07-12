package com.fitouts.qto.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitouts.shared.enums.QtoLineSource;
import com.fitouts.shared.enums.QtoLineType;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QtoLineRequest {
    private UUID id;
    private QtoLineType lineType;
    private String label;
    private BigDecimal quantity;
    private String unit;
    private UUID workItemId;
    private BigDecimal rate;
    private String geometryJson;
    private QtoLineSource source;
    private Integer sortOrder;
}
