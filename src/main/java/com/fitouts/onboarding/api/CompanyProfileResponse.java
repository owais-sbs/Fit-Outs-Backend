package com.fitouts.onboarding.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyProfileResponse {

    private String companyName;
    private String logoUrl;
    private boolean onboardingCompleted;
}
