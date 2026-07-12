package com.fitouts.qto.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fitouts.shared.enums.QtoSessionStatus;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QtoSessionResponse {
    private UUID id;
    private Long projectId;
    private UUID drawingId;
    private String drawingName;
    private QtoSessionStatus status;
    private BigDecimal scaleRatio;
    private String scaleUnit;
    private String notes;
    private List<QtoLineResponse> lines;
}
