package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PermitTypeResponse {

    private UUID uuid;
    private String code;
    private String name;
    private String authorityCode;
    private String authorityName;
    private String authorityType;
    private String typicalTrigger;
    private List<String> prerequisitePermitCodes;
    private String prerequisiteNotes;
    private Integer indicativeSlaDaysMin;
    private Integer indicativeSlaDaysMax;
    private String indicativeSlaRaw;
    private Integer actualMedianSlaDays;
    private int completedCaseCount;
    /** The SLA the planner actually uses: the observed median once 10 cases have closed. */
    private Integer planningSlaDays;
    private boolean usingActualSla;
    private Integer validityDaysMin;
    private Integer validityDaysMax;
    private String validityRaw;
    private boolean hasDeposit;
    private boolean depositConditional;
    private BigDecimal depositAmountIndicative;
    private BigDecimal feeIndicative;
    private boolean renewable;
    private String blocksActivitiesRaw;
    private List<String> blocksActivityCodes;
    private List<String> requiredDocumentCodes;
    private boolean active;
    private String verifiedBy;
    private LocalDate verifiedDate;
    private String sourceUrl;
    private boolean tenantOwned;
}
