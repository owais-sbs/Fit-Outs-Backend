package com.fitouts.billing.api;

import lombok.Data;

@Data
public class PaymentRejectRequest {
    private String reason;
}
