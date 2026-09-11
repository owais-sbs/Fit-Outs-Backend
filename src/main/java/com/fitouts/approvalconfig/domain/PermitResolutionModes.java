package com.fitouts.approvalconfig.domain;

public final class PermitResolutionModes {

    public static final String ANY_ONE_APPLIES = "ANY_ONE_APPLIES";
    public static final String ALL_REQUIRED = "ALL_REQUIRED";

    private PermitResolutionModes() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return ANY_ONE_APPLIES;
        }
        return switch (value.trim().toUpperCase().replace(' ', '_').replace('-', '_')) {
            case "ALL_REQUIRED", "ALL", "ALL_APPLY" -> ALL_REQUIRED;
            default -> ANY_ONE_APPLIES;
        };
    }
}
