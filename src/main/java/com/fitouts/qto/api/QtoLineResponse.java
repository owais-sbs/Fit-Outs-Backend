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
public class QtoLineResponse {
    private UUID id;
    private QtoLineType lineType;
    private String label;
    private BigDecimal quantity;
    private String unit;
    private UUID workItemId;
    private String workItemName;
    private BigDecimal rate;
    private BigDecimal amount;
    private String geometryJson;
    private QtoLineSource source;
    private Boolean editable;
    private Integer sortOrder;
}
