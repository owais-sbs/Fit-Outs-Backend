package com.fitouts.tenant.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.tenant.domain.TenantStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantResponse {

    private UUID uuid;
    private String companyName;
    private String logo;
    private String domainSlug;
    private UUID subscriptionPlanUuid;
    private TenantStatus status;
    private OffsetDateTime createdAt;
}
