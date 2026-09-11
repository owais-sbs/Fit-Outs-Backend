package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScRfqSummaryResponse {
    private UUID packageUuid;
    private String packageName;
    private Long projectId;
    private String projectName;
    private String tenderStatus;
    private OffsetDateTime tenderDeadline;
    private OffsetDateTime tenderIssuedAt;
    private Integer quoteValidityDays;
    private String paymentTerms;
    private BigDecimal retentionPct;
    private OffsetDateTime siteVisitAt;
    private String tenderDescription;
    private String bidderStatus;
    private boolean sealed;
    private boolean deadlinePassed;
}
