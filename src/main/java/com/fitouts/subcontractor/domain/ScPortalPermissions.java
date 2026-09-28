package com.fitouts.subcontractor.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Toggleable SC portal permissions. Role templates seed defaults; org admins may add/remove.
 * Main-contractor tender evaluation is never grantable.
 */
public final class ScPortalPermissions {

    public static final String VIEW_RFQS = "VIEW_RFQS";
    public static final String VIEW_TENDER_BOQ = "VIEW_TENDER_BOQ";
    public static final String DRAFT_QUOTE = "DRAFT_QUOTE";
    public static final String SUBMIT_QUOTE = "SUBMIT_QUOTE";
    public static final String CLARIFICATIONS = "CLARIFICATIONS";
    public static final String VIEW_ADDENDA = "VIEW_ADDENDA";
    public static final String MY_BIDS = "MY_BIDS";

    public static final String VIEW_AWARDED_PACKAGE = "VIEW_AWARDED_PACKAGE";
    public static final String PROGRESS_ENTRY = "PROGRESS_ENTRY";
    public static final String MANPOWER = "MANPOWER";
    public static final String MATERIAL_REQUESTS = "MATERIAL_REQUESTS";
    public static final String SNAGS = "SNAGS";
    public static final String INSPECTIONS = "INSPECTIONS";
    public static final String HSE = "HSE";

    public static final String VIEW_COMMERCIAL_BOQ = "VIEW_COMMERCIAL_BOQ";
    public static final String CLAIMS = "CLAIMS";
    public static final String CERTIFICATES = "CERTIFICATES";
    public static final String RETENTION = "RETENTION";
    public static final String BACK_CHARGES = "BACK_CHARGES";
    public static final String PAYMENT_STATUS = "PAYMENT_STATUS";

    public static final String METHOD_STATEMENTS = "METHOD_STATEMENTS";
    public static final String SHOP_DRAWINGS = "SHOP_DRAWINGS";
    public static final String MATERIAL_SUBMITTALS = "MATERIAL_SUBMITTALS";
    public static final String REVISIONS = "REVISIONS";
    public static final String AS_BUILTS = "AS_BUILTS";
    public static final String OM_DOCS = "OM_DOCS";

    private static final Set<String> ALL = Set.of(
            VIEW_RFQS, VIEW_TENDER_BOQ, DRAFT_QUOTE, SUBMIT_QUOTE, CLARIFICATIONS, VIEW_ADDENDA, MY_BIDS,
            VIEW_AWARDED_PACKAGE, PROGRESS_ENTRY, MANPOWER, MATERIAL_REQUESTS, SNAGS, INSPECTIONS, HSE,
            VIEW_COMMERCIAL_BOQ, CLAIMS, CERTIFICATES, RETENTION, BACK_CHARGES, PAYMENT_STATUS,
            METHOD_STATEMENTS, SHOP_DRAWINGS, MATERIAL_SUBMITTALS, REVISIONS, AS_BUILTS, OM_DOCS);

    private static final Set<String> TENDERING = Set.of(
            VIEW_RFQS, VIEW_TENDER_BOQ, DRAFT_QUOTE, SUBMIT_QUOTE, CLARIFICATIONS, VIEW_ADDENDA, MY_BIDS);

    private static final Set<String> EXECUTION = Set.of(
            VIEW_AWARDED_PACKAGE, PROGRESS_ENTRY, MANPOWER, MATERIAL_REQUESTS, SNAGS, INSPECTIONS, HSE);

    private static final Set<String> COMMERCIAL = Set.of(
            VIEW_COMMERCIAL_BOQ, CLAIMS, CERTIFICATES, RETENTION, BACK_CHARGES, PAYMENT_STATUS);

    private static final Set<String> DOCUMENTS = Set.of(
            METHOD_STATEMENTS, SHOP_DRAWINGS, MATERIAL_SUBMITTALS, REVISIONS, AS_BUILTS, OM_DOCS);

    private ScPortalPermissions() {}

    public static List<String> defaultsForRole(ScPortalRole role) {
        if (role == null) {
            return List.of();
        }
        return switch (role) {
            case SC_ADMIN -> new ArrayList<>(ALL);
            case SC_ESTIMATOR -> List.of(
                    VIEW_RFQS, VIEW_TENDER_BOQ, DRAFT_QUOTE, SUBMIT_QUOTE, CLARIFICATIONS, VIEW_ADDENDA, MY_BIDS);
            case SC_SUPERVISOR -> List.of(
                    VIEW_AWARDED_PACKAGE, PROGRESS_ENTRY, MANPOWER, MATERIAL_REQUESTS, SNAGS, INSPECTIONS, HSE);
            case SC_QS -> List.of(
                    VIEW_COMMERCIAL_BOQ, CLAIMS, CERTIFICATES, RETENTION, BACK_CHARGES, PAYMENT_STATUS);
            case SC_DOC_CONTROLLER -> List.of(
                    METHOD_STATEMENTS, SHOP_DRAWINGS, MATERIAL_SUBMITTALS, REVISIONS, AS_BUILTS, OM_DOCS);
        };
    }

    public static List<String> sanitize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String key = item.trim().toUpperCase(Locale.ROOT);
            if (ALL.contains(key)) {
                out.add(key);
            }
        }
        return List.copyOf(out);
    }

    public static String toJson(List<String> permissions) {
        List<String> clean = sanitize(permissions);
        if (clean.isEmpty()) {
            return null;
        }
        return clean.stream().collect(Collectors.joining(","));
    }

    public static List<String> fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            // tolerate simple JSON array: ["A","B"]
            trimmed = trimmed.replace("[", "").replace("]", "").replace("\"", "");
        }
        return sanitize(Arrays.asList(trimmed.split(",")));
    }

    public static List<String> resolve(ScPortalRole role, String storedJson, List<String> requestPermissions) {
        if (requestPermissions != null) {
            return sanitize(requestPermissions);
        }
        List<String> stored = fromJson(storedJson);
        if (!stored.isEmpty()) {
            return stored;
        }
        return defaultsForRole(role);
    }

    public static boolean canAccessTendering(List<String> permissions) {
        return !Collections.disjoint(permissions, TENDERING);
    }

    public static boolean canAccessExecution(List<String> permissions) {
        return !Collections.disjoint(permissions, EXECUTION);
    }

    public static boolean canAccessCommercial(List<String> permissions) {
        return !Collections.disjoint(permissions, COMMERCIAL);
    }

    public static boolean canAccessDocuments(List<String> permissions) {
        return !Collections.disjoint(permissions, DOCUMENTS);
    }
}
