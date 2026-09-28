package com.fitouts.onboarding.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.auth.domain.AccessPhase;
import com.fitouts.subscription.domain.SubscriptionPaymentStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubmitPaymentResponse {

    private UUID paymentUuid;
    private UUID companyUuid;
    private UUID planUuid;
    private String planName;
    private BigDecimal amount;
    private String paymentMethod;
    private SubscriptionPaymentStatus status;
    private OffsetDateTime createdAt;
    private AccessPhase accessPhase;
    private boolean alreadyPending;
}
