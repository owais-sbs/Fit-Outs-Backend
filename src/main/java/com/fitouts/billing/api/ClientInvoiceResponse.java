package com.fitouts.billing.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.billing.domain.BillingStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientInvoiceResponse {
    private UUID paymentRequestUuid;
    private UUID milestoneUuid;
    private String milestoneName;
    private BigDecimal amount;
    private BillingStatus status;
    private String notes;
    private OffsetDateTime issuedAt;
    private OffsetDateTime updatedAt;
}
