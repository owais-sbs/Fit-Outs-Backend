package com.fitouts.subcontractor.api;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScAppointmentEligibilityResponse {

    private final boolean eligible;
    private final List<String> blockingReasons;
    private final String complianceStatus;
    private final String companyStatus;
}
