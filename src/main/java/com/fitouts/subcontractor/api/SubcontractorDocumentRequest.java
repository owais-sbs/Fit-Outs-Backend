package com.fitouts.subcontractor.api;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorDocumentRequest {

    private String documentTypeId;
    private String referenceNo;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String status;
}
