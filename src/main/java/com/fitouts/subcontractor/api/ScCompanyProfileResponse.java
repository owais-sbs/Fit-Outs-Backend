package com.fitouts.subcontractor.api;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScCompanyProfileResponse {

    private final UUID uuid;
    private final Long adminAccountId;
    private final String legalCompanyName;
    private final String tradeLicenceNumber;
    private final String tradeLicenceFilePath;
    private final String tradeLicenceExpiry;
    private final String trn;
    private final String registeredAddress;
    private final String primaryContactName;
    private final String primaryContactEmail;
    private final String primaryContactPhone;
    private final String accountsContactName;
    private final String accountsContactEmail;
    private final String accountsContactPhone;
    private final List<String> tradeCategories;
    private final String declaredCapacity;
    private final String status;
    private final String complianceStatus;
    private final List<ScComplianceDocumentResponse> complianceDocuments;
    private final ScOrganizationExtensionResponse organizationExtension;
}
