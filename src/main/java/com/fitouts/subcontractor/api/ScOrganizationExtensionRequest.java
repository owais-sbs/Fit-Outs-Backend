package com.fitouts.subcontractor.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScOrganizationExtensionRequest {

    private String tradeLicenceAuthority;
    private String tradeLicenceActivities;
    private String establishmentCardExpiry;
    private String locationPin;
    private String monthlyCapacityValue;
    private Integer monthlyCapacityManpower;
    private Integer yearsInOperation;
    private String annualTurnoverBand;
    private String workshopAddress;
    private Integer hseLtiCount;
    private String hseOfficerName;
    private String hseOfficerCertExpiry;
    private String paymentTermsAccepted;
    private String retentionPctAccepted;
    private String advancePaymentRequired;
    private Integer workforceTotal;
    private String workforceTradeBreakdown;
    private Boolean crossTenantVisible;
}
