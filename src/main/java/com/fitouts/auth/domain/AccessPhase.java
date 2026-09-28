package com.fitouts.auth.domain;

/**
 * Self-serve subscriber progress after login.
 * SUPER_ADMIN and fully provisioned staff map to {@link #PORTAL}.
 */
public enum AccessPhase {
    /** Needs plan selection / payment confirmation. */
    SUBSCRIBE,
    /** Payment confirmed; must complete company name + logo. */
    ONBOARDING,
    /** Full product access. */
    PORTAL
}
