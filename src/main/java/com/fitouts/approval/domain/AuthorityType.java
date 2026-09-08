package com.fitouts.approval.domain;

/**
 * The three layers of permission described in the spec, plus utilities.
 * Community permits name a type rather than a specific authority, because which
 * master developer applies depends on the project's community.
 */
public enum AuthorityType {
    REGULATOR,
    MASTER_DEVELOPER,
    BUILDING_MANAGEMENT,
    UTILITY,
    SPECIAL;

    public static AuthorityType fromLabel(String label) {
        if (label == null) return SPECIAL;
        String normalised = label.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (normalised) {
            case "REGULATOR" -> REGULATOR;
            case "MASTER_DEVELOPER" -> MASTER_DEVELOPER;
            case "BUILDING_MANAGEMENT" -> BUILDING_MANAGEMENT;
            case "UTILITY" -> UTILITY;
            default -> SPECIAL;
        };
    }
}
