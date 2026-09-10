package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScQuoteResponse {
    private UUID uuid;
    private UUID packageUuid;
    private UUID organizationUuid;
    private int version;
    private String status;
    private BigDecimal totalValue;
    private Integer leadTimeDays;
    private String exclusionsText;
    private String qualificationsText;
    private LocalDate validityDate;
    private OffsetDateTime submittedAt;
    private List<ScQuoteLineResponse> lines;
    private boolean ratesVisible;
}
