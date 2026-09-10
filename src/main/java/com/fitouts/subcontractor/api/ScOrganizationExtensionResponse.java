package com.fitouts.subcontractor.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScOrganizationExtensionResponse {

    private final UUID organizationUuid;
    private final String tradeLicenceAuthority;
    private final String tradeLicenceActivities;
    private final String establishmentCardExpiry;
    private final String establishmentCardFilePath;
    private final String vatCertificateFilePath;
    private final String locationPin;
    private final BigDecimal monthlyCapacityValue;
    private final Integer monthlyCapacityManpower;
    private final Integer yearsInOperation;
    private final String annualTurnoverBand;
    private final String workshopAddress;
    private final String hsePolicyFilePath;
    private final Integer hseLtiCount;
    private final String hseOfficerName;
    private final String hseOfficerCertExpiry;
    private final String hseOfficerCertFilePath;
    private final String paymentTermsAccepted;
    private final BigDecimal retentionPctAccepted;
    private final BigDecimal advancePaymentRequired;
    private final Integer workforceTotal;
    private final String workforceTradeBreakdown;
    private final boolean crossTenantVisible;
    private final ScBankDetailResponse bankDetail;
    private final List<ScCommunityRegistrationResponse> communityRegistrations;
    private final List<ScOrganizationReferenceResponse> references;
    private final ScPortalContextResponse portalContext;
}
