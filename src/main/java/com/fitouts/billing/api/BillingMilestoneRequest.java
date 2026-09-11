package com.fitouts.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fitouts.billing.domain.BillingStatus;

import lombok.Data;

@Data
public class BillingMilestoneRequest {
    private String name;
    private BigDecimal amount;
    private LocalDate dueDate;
    /** Optional schedule activity UUID as plain text; validated in service layer. */
    private String linkedActivityUuid;
    private BillingStatus status;
    private BigDecimal percentCompleteRequired;
    /** BOQ_TEMPLATE, SCHEDULE_APPLY, or MANUAL. */
    private String setupSource;
}
