package com.fitouts.subscription.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subscription.domain.SubscriptionPaymentStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionPaymentResponse {

    private UUID uuid;
    private UUID companyUuid;
    private String companyName;
    private UUID planUuid;
    private String planName;
    private BigDecimal amount;
    private String paymentMethod;
    private SubscriptionPaymentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime decidedAt;
    private Long decidedByAccountId;
    private String decidedByName;
}
