package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScRetentionLedgerResponse {
    private UUID uuid;
    private UUID packageUuid;
    private UUID organizationUuid;
    private Long projectId;
    private BigDecimal amountHeld;
    private LocalDate releaseDate;
    private LocalDate defectLiabilityEnd;
    private String status;
    private UUID certificateUuid;
    private String notes;
    private OffsetDateTime createdAt;
}
