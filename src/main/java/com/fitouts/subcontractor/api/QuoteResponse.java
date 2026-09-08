package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteResponse {

    private UUID id;
    private UUID packageId;
    private UUID subcontractorId;
    private String subcontractorName;
    private String status;
    private BigDecimal totalAmount;
    private String remarks;
    private OffsetDateTime submittedAt;
    private OffsetDateTime createdAt;
    private List<QuoteLineResponse> lines;
}
