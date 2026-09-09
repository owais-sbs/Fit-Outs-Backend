package com.fitouts.approvalconfig.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthorityRequest {
    private String code;
    private String name;
    private String type;
    private String emirate;
    private String jurisdictionAreas;
    private String permitsIssued;
    private String submissionChannel;
    private String notes;
    private Boolean requiresCompanyRegistration;
    private Boolean active;
}
