package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScComplianceDocumentResponse {

    private final UUID uuid;
    private final String documentType;
    private final String documentLabel;
    private final String referenceNo;
    private final String expiryDate;
    private final String filePath;
    private final boolean requiredForAppointment;
    private final String itemStatus;
}
