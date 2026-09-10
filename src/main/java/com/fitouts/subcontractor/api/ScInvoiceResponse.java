package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subcontractor.domain.ScInvoiceStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScInvoiceResponse {
    private UUID uuid;
    private UUID packageUuid;
    private Long projectId;
    private UUID companyId;
    private UUID claimUuid;
    private String invoiceNumber;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private String currency;
    private String notes;
    private ScInvoiceStatus status;
    private String attachmentPaths;
    private Long submittedBy;
    private OffsetDateTime submittedAt;
    private Long decidedBy;
    private OffsetDateTime decidedAt;
    private OffsetDateTime paidAt;
    private String paymentReference;
    private String reason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String packageName;
    private String projectName;
    private BigDecimal totalAmount;
}
