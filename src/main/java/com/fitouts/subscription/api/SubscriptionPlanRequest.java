package com.fitouts.subscription.api;

import java.math.BigDecimal;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionPlanRequest {

    @NotBlank
    private String planName;

    @NotNull
    @Positive
    private Integer maxUsers;

    @NotEmpty
    private Set<@NotBlank String> modulesIncluded;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal priceMonthly;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal priceAnnual;

    private Boolean active;
}
