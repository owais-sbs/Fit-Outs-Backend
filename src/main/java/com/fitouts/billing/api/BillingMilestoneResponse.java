package com.fitouts.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.billing.domain.BillingStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BillingMilestoneResponse {
    private UUID uuid;
    private Long projectId;
    private UUID companyId;
    private String name;
    private BigDecimal amount;
    private LocalDate dueDate;
    private UUID linkedActivityUuid;
    private BillingStatus status;
    private BigDecimal percentCompleteRequired;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
