package com.fitouts.approval.api;

import java.time.LocalDate;

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
    private String permitsIssuedTypical;
    private String submissionChannel;
    private String portalUrl;
    private String notes;
    private Boolean active;
    private String verifiedBy;
    private LocalDate verifiedDate;
    private String sourceUrl;
}
