package com.fitouts.subcontractor.domain;

public enum SubcontractorClaimStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    MEASURED,
    CERTIFIED,
    /** @deprecated Payment belongs on the certificate/invoice; kept for legacy rows only. */
    PAID
}
