package com.fitouts.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fitouts.billing.domain.BillingStatus;

import lombok.Data;

@Data
public class BillingMilestoneRequest {
    private String name;
    private BigDecimal amount;
    private LocalDate dueDate;
    private UUID linkedActivityUuid;
    private BillingStatus status;
    private BigDecimal percentCompleteRequired;
}
