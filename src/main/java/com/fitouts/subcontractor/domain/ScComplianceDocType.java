package com.fitouts.subcontractor.domain;

public enum ScComplianceDocType {
    CAR_INSURANCE,
    TPL_INSURANCE,
    WC_INSURANCE,
    CIVIL_DEFENCE,
    SIRA,
    DEWA_ELECTRICAL,
    MUNICIPALITY_CLASSIFICATION,
    ISO_9001,
    ISO_45001,
    ISO_14001,
    CHAMBER_OF_COMMERCE;

    public String displayName() {
        return switch (this) {
            case CAR_INSURANCE -> "Contractors All Risks (CAR) insurance";
            case TPL_INSURANCE -> "Third Party Liability (TPL) insurance";
            case WC_INSURANCE -> "Workmen's Compensation insurance";
            case CIVIL_DEFENCE -> "Civil Defence certificate";
            case SIRA -> "SIRA licence";
            case DEWA_ELECTRICAL -> "DEWA-enrolled electrical contractor certificate";
            case MUNICIPALITY_CLASSIFICATION -> "Municipality contractor classification";
            case ISO_9001 -> "ISO 9001 certificate";
            case ISO_45001 -> "ISO 45001 certificate";
            case ISO_14001 -> "ISO 14001 certificate";
            case CHAMBER_OF_COMMERCE -> "Chamber of commerce membership";
        };
    }
}
