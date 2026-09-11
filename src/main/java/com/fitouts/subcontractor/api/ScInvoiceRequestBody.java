package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class ScInvoiceRequestBody {
    private UUID packageUuid;
    private UUID claimUuid;
    private String invoiceNumber;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private String currency;
    private String notes;
}
