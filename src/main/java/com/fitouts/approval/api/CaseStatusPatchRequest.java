package com.fitouts.approval.api;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** A validated status move plus whatever fields that move requires. */
@Getter
@Setter
public class CaseStatusPatchRequest {

    private String status;
    private Long assignedToAccountId;
    private LocalDate targetSubmissionDate;
    private String authorityReference;
    private String permitNumber;
    private String permitFilePath;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private LocalDate approvedDate;
    private List<String> linkedActivityUuids;
    private String notes;
    /** Required when moving to REJECTED, WITHDRAWN or CLOSED. */
    private String reason;
}
