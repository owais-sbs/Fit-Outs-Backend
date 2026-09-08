package com.fitouts.approval.api;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyComplianceRequest {

    private String documentTypeCode;
    private String referenceNo;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String filePath;
    private String status;
    private Long renewalOwnerAccountId;
    private String notes;
}
