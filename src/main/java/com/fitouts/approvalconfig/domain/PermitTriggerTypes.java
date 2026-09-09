package com.fitouts.approvalconfig.domain;

public final class PermitTriggerTypes {

    public static final String SCOPE_TAG = "SCOPE_TAG";
    public static final String LOCATION = "LOCATION";
    public static final String PROPERTY_PROJECT = "PROPERTY_PROJECT";
    public static final String PREREQUISITE = "PREREQUISITE";
    public static final String COMPANY = "COMPANY";

    private PermitTriggerTypes() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return SCOPE_TAG;
        }
        return switch (value.trim().toUpperCase().replace(' ', '_').replace('-', '_')) {
            case "SCOPE_TAG", "SCOPETAG" -> SCOPE_TAG;
            case "LOCATION", "LOCATION_ONLY" -> LOCATION;
            case "PROPERTY_PROJECT", "PROPERTY_OR_PROJECT_TYPE", "PROPERTY" -> PROPERTY_PROJECT;
            case "PREREQUISITE", "FOLLOWS_A_PREREQUISITE", "FOLLOWS_PREREQUISITE" -> PREREQUISITE;
            case "COMPANY", "COMPANY_LEVEL" -> COMPANY;
            default -> SCOPE_TAG;
        };
    }
}
