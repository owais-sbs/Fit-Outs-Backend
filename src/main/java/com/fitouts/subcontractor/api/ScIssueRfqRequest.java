package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class ScIssueRfqRequest {
    private OffsetDateTime tenderDeadline;
    private Integer quoteValidityDays;
    private String paymentTerms;
    private BigDecimal retentionPct;
    private OffsetDateTime siteVisitAt;
    private String tenderDescription;
}
