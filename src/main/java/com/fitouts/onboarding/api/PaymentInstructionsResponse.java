package com.fitouts.onboarding.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentInstructionsResponse {

    private String bankName;
    private String accountName;
    private String accountNumber;
    private String iban;
    private String swift;
    private String upiId;
    private String instructions;
}
