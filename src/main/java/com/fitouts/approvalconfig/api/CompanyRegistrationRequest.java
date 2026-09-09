package com.fitouts.approvalconfig.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyRegistrationRequest {
    private UUID authorityId;
    private String referenceNo;
    private LocalDate registrationDate;
    private LocalDate renewalDate;
    private String status;
    private String notes;
    private Boolean active;
}
