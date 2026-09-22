package com.fitouts.completion.api;

import java.time.LocalDate;

import lombok.Data;

@Data
public class DlpUpdateRequest {
    private LocalDate dlpStartDate;
    private LocalDate dlpEndDate;
    private Integer dlpDurationMonths;
}
