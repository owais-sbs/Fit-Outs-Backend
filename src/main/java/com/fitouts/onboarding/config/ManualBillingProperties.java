package com.fitouts.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "fitouts.billing.manual")
public class ManualBillingProperties {

    private String bankName = "Fitouts Platform Bank";
    private String accountName = "Fitouts Subscriptions";
    private String accountNumber = "0000000000";
    private String iban = "";
    private String swift = "";
    private String upiId = "";
    private String instructions = "Transfer the plan amount using the details below, then tap I've paid so we can confirm your subscription.";
}
