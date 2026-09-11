package com.fitouts.subcontractor.domain;

public enum ScOrgDocumentCode {
    ESTABLISHMENT_CARD,
    VAT_CERTIFICATE,
    HSE_POLICY,
    HSE_OFFICER_CERT,
    WORKSHOP_PHOTO,
    ORG_CHART,
    CHAMBER_OF_COMMERCE;

    public String displayName() {
        return switch (this) {
            case ESTABLISHMENT_CARD -> "Establishment / immigration card";
            case VAT_CERTIFICATE -> "VAT registration certificate";
            case HSE_POLICY -> "HSE policy";
            case HSE_OFFICER_CERT -> "HSE officer certification";
            case WORKSHOP_PHOTO -> "Workshop / factory photo";
            case ORG_CHART -> "Organisation chart";
            case CHAMBER_OF_COMMERCE -> "Chamber of commerce membership";
        };
    }
}
