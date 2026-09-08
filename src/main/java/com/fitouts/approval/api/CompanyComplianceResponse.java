package com.fitouts.approval.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyComplianceResponse {

    private UUID uuid;
    private String documentTypeCode;
    private String documentTypeName;
    private String category;
    private String referenceNo;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String filePath;
    private String status;
    private Long renewalOwnerAccountId;
    private String notes;
    /** Negative once the document has lapsed, so the UI can colour it without recomputing. */
    private Long daysToExpiry;
}
