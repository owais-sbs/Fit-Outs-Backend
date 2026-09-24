package com.fitouts.subcontractor.api;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScEligibilityCheckResponse {
    private String code;
    private String label;
    private String result;
    private boolean blocking;
    private LocalDate expiryDate;
    private String reason;
}
