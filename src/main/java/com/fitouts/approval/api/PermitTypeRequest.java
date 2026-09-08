package com.fitouts.approval.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermitTypeRequest {

    private String code;
    private String name;
    private String authorityCode;
    private String authorityType;
    private String typicalTrigger;
    private List<String> prerequisitePermitCodes;
    private String prerequisiteNotes;
    private Integer indicativeSlaDaysMin;
    private Integer indicativeSlaDaysMax;
    private Integer validityDaysMin;
    private Integer validityDaysMax;
    private Boolean hasDeposit;
    private Boolean depositConditional;
    private BigDecimal depositAmountIndicative;
    private BigDecimal feeIndicative;
    private Boolean renewable;
    private List<String> blocksActivityCodes;
    private List<String> requiredDocumentCodes;
    private Boolean active;
    private String verifiedBy;
    private LocalDate verifiedDate;
    private String sourceUrl;
}
