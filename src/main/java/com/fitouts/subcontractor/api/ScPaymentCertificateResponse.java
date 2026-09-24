package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScPaymentCertificateResponse {
    private UUID uuid;
    private UUID claimUuid;
    private UUID packageUuid;
    private Long projectId;
    private UUID organizationUuid;
    private BigDecimal certifiedValue;
    private BigDecimal retentionHeld;
    private BigDecimal backChargesApplied;
    private BigDecimal otherDeductions;
    private BigDecimal netPayable;
    private String status;
    private String certificateNumber;
    private LocalDate certificateDate;
    private LocalDate paidDate;
    private BigDecimal paidAmount;
    private String accountingRef;
    private String paymentNotes;
    private OffsetDateTime createdAt;
    private String packageName;
    private String claimNumber;
    private UUID invoiceUuid;
}
