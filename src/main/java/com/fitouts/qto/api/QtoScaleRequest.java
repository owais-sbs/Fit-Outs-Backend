package com.fitouts.qto.api;

import java.math.BigDecimal;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QtoScaleRequest {
    private BigDecimal scaleRatio;
    private String scaleUnit;
}
