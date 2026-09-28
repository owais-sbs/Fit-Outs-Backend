package com.fitouts.auth.api;

import java.util.Set;
import java.util.UUID;

import com.fitouts.auth.domain.AccessPhase;
import com.fitouts.auth.domain.Role;
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.employee.domain.Feature;
import com.fitouts.subscription.domain.SubscriptionPaymentStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrentUserResponse {

    private Long id;
    private UUID companyId;
    private String companyName;
    private String fullName;
    private String email;
    private String phone;
    private Set<Role> roles;
    private Set<Feature> enabledFeatures;
    private AccessPhase accessPhase;
    private CompanyStatus companyStatus;
    private Boolean onboardingCompleted;
    private SubscriptionPaymentStatus pendingPaymentStatus;
    /** Same-origin URL for company logo, e.g. `/api/files/...`. */
    private String companyLogo;
}
