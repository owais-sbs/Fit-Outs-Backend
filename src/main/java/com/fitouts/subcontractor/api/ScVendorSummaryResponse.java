package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScVendorSummaryResponse {

    private final UUID organizationUuid;
    private final String legalCompanyName;
    private final String primaryContactEmail;
    private final String status;
    private final String complianceStatus;
    private final BigDecimal performanceScore;
    private final List<String> tradeCategories;
    private final List<String> approvedTrades;
    private final BigDecimal maxPackageValue;
    private final String reviewedAt;
    private final String prequalificationNotes;
}
