package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class ScRetentionReleaseRequest {
    private BigDecimal amountReleased;
    private LocalDate releaseDate;
    private String notes;
}
