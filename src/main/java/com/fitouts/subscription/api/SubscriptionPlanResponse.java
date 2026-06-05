package com.fitouts.subscription.api;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionPlanResponse {

    private UUID uuid;
    private String planName;
    private Integer maxUsers;
    private Set<String> modulesIncluded;
    private BigDecimal priceMonthly;
    private BigDecimal priceAnnual;
    private Boolean active;
}
