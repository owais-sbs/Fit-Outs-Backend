package com.fitouts.schedule.api;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ProgressUpdateRequest {
    private Integer percentComplete;
    private String notes;
    private BigDecimal labourHours;
}
