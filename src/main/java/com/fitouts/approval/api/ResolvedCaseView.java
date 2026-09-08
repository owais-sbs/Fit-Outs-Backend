package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** One case the resolver proposes, before anything is written. */
@Getter
@Builder
public class ResolvedCaseView {

    private String permitTypeCode;
    private String permitTypeName;
    private String authorityCode;
    private String authorityName;
    private String authorityType;
    private Integer slaDays;
    private String slaSource;
    private Integer validityDays;
    private boolean hasDeposit;
    private boolean depositConditional;
    private BigDecimal depositAmountIndicative;
    private BigDecimal feeIndicative;
    private List<String> prerequisitePermitCodes;
    private List<String> requiredDocumentCodes;
    private String blocksActivitiesRaw;
    private String triggerReason;
    /** True when a case for this permit already exists on the project. */
    private boolean alreadyExists;
}
