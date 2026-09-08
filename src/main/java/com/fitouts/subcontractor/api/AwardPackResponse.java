package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AwardPackResponse {

    private UUID awardId;
    private UUID packageId;
    private String packageRef;
    private String title;
    private UUID subcontractorId;
    private String subcontractorName;
    private BigDecimal agreedAmount;
    private String scope;
    private UUID pricedBoqDocumentId;
    private List<PricedBoqLineSummary> pricedBoqLines;
    private String drawingRevision;
    private String programmeExtract;
    private String siteRules;
    private OffsetDateTime awardedAt;
    private String status;

    @Getter
    @Setter
    public static class PricedBoqLineSummary {
        private UUID boqLineId;
        private String sectionCode;
        private String description;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal unitRate;
        private BigDecimal amount;
    }
}
