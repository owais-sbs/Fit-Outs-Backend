package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageAwardRequest {

    private UUID subcontractorId;
    private UUID winningQuoteId;
    private BigDecimal agreedAmount;
    private String scope;
    private UUID pricedBoqDocumentId;
    private String drawingRevision;
    private String programmeExtract;
    private String siteRules;
}
