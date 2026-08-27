package com.fitouts.billing.api;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class RequestPaymentBody {
    private BigDecimal amount;
    private String notes;
}
