package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class ScInvoiceRequestBody {
    private UUID packageUuid;
    private UUID claimUuid;
    /** Required for Module 26: invoice against a PAYABLE certificate. */
    private UUID certificateUuid;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private String currency;
    private String notes;
}
