package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseSubmissionResponse {

    private UUID uuid;
    private int version;
    private Long submittedBy;
    private LocalDate submittedDate;
    private String channel;
    private String receiptFilePath;
    private BigDecimal feeAmount;
    private String outcome;
    private LocalDate outcomeDate;
    private Integer turnaroundDays;
    private String notes;
}
