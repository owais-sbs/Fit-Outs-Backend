package com.fitouts.company.api;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.employee.domain.Feature;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyResponse {

    private UUID uuid;
    private String companyName;
    private String logo;
    private String stampImagePath;
    private String signatureImagePath;
    private String domainSlug;
    private UUID subscriptionPlanUuid;
    private String subscriptionPlanName;
    private Set<Feature> enabledFeatures;
    private CompanyStatus status;
    private OffsetDateTime createdAt;
    private String adminEmail;
    private String temporaryPassword;
    private Boolean inviteEmailSent;
}
