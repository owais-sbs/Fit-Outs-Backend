package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fitouts.subcontractor.domain.PackageStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NormalizedComparisonResponse {

    private UUID packageId;
    private String packageRef;
    private String title;
    private PackageStatus status;
    private Boolean bidsOpened;
    private List<BidderSummary> bidders;
    private List<ComparisonLine> lines;

    @Getter
    @Setter
    public static class BidderSummary {
        private UUID subcontractorId;
        private String subcontractorName;
        private UUID quoteId;
        private BigDecimal totalAmount;
        private String status;
        private OffsetDateTime submittedAt;
    }

    @Getter
    @Setter
    public static class ComparisonLine {
        private UUID boqLineId;
        private String sectionCode;
        private String description;
        private BigDecimal quantity;
        private String unit;
        private Map<UUID, BigDecimal> rates; // subcontractorId -> unit rate (or null if sealed/unquoted)
        private Map<UUID, BigDecimal> amounts; // subcontractorId -> total line amount
    }
}
