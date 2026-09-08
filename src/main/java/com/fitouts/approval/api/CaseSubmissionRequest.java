package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CaseSubmissionRequest {

    private LocalDate submittedDate;
    private String channel;
    private String authorityReference;
    private String receiptFilePath;
    private BigDecimal feeAmount;
    private String notes;
    /** Records the outcome straight away when the authority responded on the spot. */
    private String outcome;
    private LocalDate outcomeDate;
}
