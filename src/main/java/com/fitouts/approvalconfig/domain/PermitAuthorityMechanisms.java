package com.fitouts.approvalconfig.domain;

/**
 * How a Permit Catalogue row finds the Authority that will issue it.
 *
 * <p>{@code FIXED} always points at one named Authority library row. The seeded FIXED
 * permits are Dubai-specific (DCD, SIRA, RTA, DMFS, DET). There is no per-emirate
 * lookup: an equivalent body in another emirate is a new Permit Type with its own
 * FIXED link, not a dynamic resolution.
 */
public final class PermitAuthorityMechanisms {

    public static final String FIXED = "FIXED";
    public static final String JURISDICTION_MASTER_DEVELOPER = "JURISDICTION_MASTER_DEVELOPER";
    public static final String JURISDICTION_BUILDING_MANAGEMENT = "JURISDICTION_BUILDING_MANAGEMENT";
    public static final String JURISDICTION_REGULATOR = "JURISDICTION_REGULATOR";
    public static final String EMIRATE_UTILITY = "EMIRATE_UTILITY";
    public static final String INHERIT_FROM_PERMIT = "INHERIT_FROM_PERMIT";
    public static final String MULTI_AUTHORITY = "MULTI_AUTHORITY";

    private PermitAuthorityMechanisms() {
    }

    /** Blank or unrecognised values stay unset — new rows must not default to a mechanism. */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase().replace(' ', '_').replace('-', '_')) {
            case "FIXED" -> FIXED;
            case "JURISDICTION_MASTER_DEVELOPER", "MASTER_DEVELOPER" -> JURISDICTION_MASTER_DEVELOPER;
            case "JURISDICTION_BUILDING_MANAGEMENT", "BUILDING_MANAGEMENT" -> JURISDICTION_BUILDING_MANAGEMENT;
            case "JURISDICTION_REGULATOR", "REGULATOR" -> JURISDICTION_REGULATOR;
            case "EMIRATE_UTILITY", "UTILITY" -> EMIRATE_UTILITY;
            case "INHERIT_FROM_PERMIT", "INHERIT_FROM_PREREQUISITE" -> INHERIT_FROM_PERMIT;
            case "MULTI_AUTHORITY", "MULTI" -> MULTI_AUTHORITY;
            default -> null;
        };
    }
}
