package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CaseFeeRequest {

    /** Set when updating an existing fee, for example recording a refund. */
    private UUID uuid;
    private String type;
    private BigDecimal amount;
    private String currency;
    private LocalDate paidDate;
    private String paymentRef;
    private String receiptFilePath;
    private String costCode;
    private Boolean refundable;
    private LocalDate refundClaimedDate;
    private LocalDate refundReceivedDate;
    private BigDecimal refundAmount;
    private String notes;
}
