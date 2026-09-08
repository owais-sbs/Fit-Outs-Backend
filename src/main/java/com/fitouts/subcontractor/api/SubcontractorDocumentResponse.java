package com.fitouts.subcontractor.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorDocumentResponse {

    private UUID id;
    private UUID subcontractorId;
    private String documentTypeId;
    private String referenceNo;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String fileId;
    private String status;
    private Long verifiedBy;
    private OffsetDateTime verifiedDate;
}
