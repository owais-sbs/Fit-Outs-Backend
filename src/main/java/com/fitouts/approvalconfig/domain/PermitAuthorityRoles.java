package com.fitouts.approvalconfig.domain;

/**
 * Jurisdiction-level roles a MULTI_AUTHORITY permit may list as candidates.
 * These are layers on the project's Jurisdiction record, not named Authority rows.
 */
public final class PermitAuthorityRoles {

    public static final String MASTER_DEVELOPER = "MASTER_DEVELOPER";
    public static final String BUILDING_MANAGEMENT = "BUILDING_MANAGEMENT";
    public static final String REGULATOR = "REGULATOR";

    private PermitAuthorityRoles() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase().replace(' ', '_').replace('-', '_')) {
            case "MASTER_DEVELOPER", "COMMUNITY" -> MASTER_DEVELOPER;
            case "BUILDING_MANAGEMENT", "BUILDING" -> BUILDING_MANAGEMENT;
            case "REGULATOR" -> REGULATOR;
            default -> null;
        };
    }

    public static String label(String role) {
        String normalised = normalize(role);
        if (MASTER_DEVELOPER.equals(normalised)) return "Master Developer";
        if (BUILDING_MANAGEMENT.equals(normalised)) return "Building Management";
        if (REGULATOR.equals(normalised)) return "Regulator";
        return role;
    }
}
