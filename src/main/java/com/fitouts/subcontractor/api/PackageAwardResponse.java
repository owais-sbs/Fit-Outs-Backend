package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageAwardResponse {

    private UUID id;
    private UUID packageId;
    private UUID subcontractorId;
    private String subcontractorName;
    private UUID winningQuoteId;
    private BigDecimal agreedAmount;
    private OffsetDateTime awardedAt;
    private Long awardedBy;
    private String scope;
    private UUID pricedBoqDocumentId;
    private String drawingRevision;
    private String programmeExtract;
    private String siteRules;
    private String status;
}
