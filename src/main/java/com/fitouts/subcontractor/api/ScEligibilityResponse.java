package com.fitouts.subcontractor.api;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScEligibilityResponse {
    private boolean eligible;
    private String status;
    private List<ScEligibilityCheckResponse> checks;
    private List<ScEligibilityCheckResponse> blockers;
    private List<ScEligibilityCheckResponse> warnings;
}
