package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseFeeResponse {

    private UUID uuid;
    private UUID caseUuid;
    private Long projectId;
    private String projectName;
    private String caseNumber;
    private String permitTypeName;
    private String authorityName;
    private String type;
    private BigDecimal amount;
    private String currency;
    private LocalDate paidDate;
    private String paymentRef;
    private String receiptFilePath;
    private String costCode;
    private boolean refundable;
    private LocalDate refundClaimedDate;
    private LocalDate refundReceivedDate;
    private BigDecimal refundAmount;
    private String notes;
    /** How long a third party has held this money. Null once refunded. */
    private Long daysOutstanding;
}
