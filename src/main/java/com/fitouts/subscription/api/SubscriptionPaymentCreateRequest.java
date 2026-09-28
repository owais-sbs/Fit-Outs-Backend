package com.fitouts.subscription.api;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubscriptionPaymentCreateRequest {

    @NotNull
    private UUID companyUuid;

    @NotNull
    private UUID planUuid;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal amount;

    @NotBlank
    private String paymentMethod;

    private String status;
}
