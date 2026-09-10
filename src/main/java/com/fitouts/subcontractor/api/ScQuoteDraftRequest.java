package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class ScQuoteDraftRequest {
    private UUID quoteUuid;
    private Integer leadTimeDays;
    private String exclusionsText;
    private String qualificationsText;
    private LocalDate validityDate;
    private List<ScQuoteLineRequest> lines;
}
