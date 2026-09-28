package com.fitouts.onboarding.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitPaymentRequest {

    @NotNull
    private UUID planUuid;

    /** MONTHLY or ANNUAL. Defaults to MONTHLY. */
    private String billingCycle;

    private String paymentMethod;
}
