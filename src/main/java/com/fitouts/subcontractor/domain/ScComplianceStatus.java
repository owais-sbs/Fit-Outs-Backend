package com.fitouts.subcontractor.domain;

/** Computed rollup of all tracked expiry dates on a subcontractor company profile. */
public enum ScComplianceStatus {
    VALID,
    EXPIRING_SOON,
    EXPIRED,
    INCOMPLETE
}
