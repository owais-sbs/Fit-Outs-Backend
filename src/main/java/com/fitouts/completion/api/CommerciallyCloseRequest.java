package com.fitouts.completion.api;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CommerciallyCloseRequest {
    private LocalDate dlpStartDate;
    private LocalDate dlpEndDate;
    /** Optional; when set with start (and no end), end is derived as start + months. */
    private Integer dlpDurationMonths;
}
