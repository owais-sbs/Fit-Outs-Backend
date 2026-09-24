package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * Full vendor dossier for QS/PM prequalification review (not the list summary).
 */
@Getter
@Builder
public class ScVendorDetailResponse {

    private final UUID organizationUuid;
    private final String legalCompanyName;
    private final String status;
    private final String complianceStatus;
    private final List<String> complianceGaps;
    private final BigDecimal performanceScore;
    private final List<String> tradeCategories;
    private final List<String> approvedTrades;
    private final BigDecimal maxPackageValue;
    private final String reviewedAt;
    private final String prequalificationNotes;

    private final String primaryContactName;
    private final String primaryContactEmail;
    private final String primaryContactPhone;
    private final String accountsContactName;
    private final String accountsContactEmail;
    private final String accountsContactPhone;
    private final String trn;
    private final String registeredAddress;
    private final String declaredCapacity;
    private final String tradeLicenceNumber;
    private final String tradeLicenceExpiry;
    private final String tradeLicenceFilePath;
    private final UUID profileUuid;

    private final List<ScComplianceDocumentResponse> complianceDocuments;
    private final ScOrganizationExtensionResponse organizationExtension;
}
