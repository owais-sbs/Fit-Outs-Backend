package com.fitouts.subcontractor.api;

import lombok.Data;

@Data
public class AppointSubcontractorRequest {
    /** Existing SC account id (optional if creating via email). */
    private Long accountId;

    /** Display / trading company name for the package. */
    private String companyName;

    /** Create or reuse SC account by email when accountId is not provided. */
    private String email;
    private String fullName;
    private String phone;
}
