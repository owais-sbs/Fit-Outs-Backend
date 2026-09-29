package com.fitouts.platformuser.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fitouts.subscription.domain.SubscriptionPaymentStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformUserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String companyName;
    private UUID companyUuid;
    private OffsetDateTime createdAt;
    private boolean subscriber;
    private SubscriptionPaymentStatus latestPaymentStatus;
    private OffsetDateTime deletionScheduledAt;
    private OffsetDateTime purgeAt;
    private Long daysUntilPurge;
    private int affectedAccountCount;
    private List<String> roles;
    private boolean deletable;
}
