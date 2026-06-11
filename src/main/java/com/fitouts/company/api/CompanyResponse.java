package com.fitouts.company.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.company.domain.CompanyStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyResponse {

    private UUID uuid;
    private String companyName;
    private String logo;
    private String domainSlug;
    private UUID subscriptionPlanUuid;
    private CompanyStatus status;
    private OffsetDateTime createdAt;
}
