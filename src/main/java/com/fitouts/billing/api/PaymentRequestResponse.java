package com.fitouts.billing.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.billing.domain.BillingStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentRequestResponse {
    private UUID uuid;
    private UUID milestoneUuid;
    private Long projectId;
    private UUID companyId;
    private BigDecimal amount;
    private BillingStatus status;
    private String notes;
    private Long requestedBy;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String milestoneName;
}
