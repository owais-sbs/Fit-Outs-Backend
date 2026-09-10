package com.fitouts.subcontractor.api;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScOrganizationReferenceRequest {

    private String clientName;
    private BigDecimal projectValue;
    private Integer yearCompleted;
    private String scopeDescription;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
}
