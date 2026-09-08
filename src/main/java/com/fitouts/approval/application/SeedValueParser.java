package com.fitouts.approval.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The seed file records SLAs, validities and quantities as human text: "5-15",
 * "30-90 days", "12 months", "Yes - refundable", "Duration of works". Nothing in it is
 * a clean integer. These helpers pull out bounds while the caller keeps the original
 * string, so the UI can still show what the source actually said.
 */
public final class SeedValueParser {

    private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*(?:-|to|–)\\s*(\\d+)");
    private static final Pattern SINGLE = Pattern.compile("(\\d+)");
    private static final Pattern PERMIT_CODE = Pattern.compile("\\bP-[A-Z]+(?:-[A-Z]+)*\\b");
    private static final Pattern DOC_CODE = Pattern.compile("\\bD\\d{2}\\b");

    private SeedValueParser() {
    }

    /** Lower bound of a range, or the single number, or null when the text has no digits. */
    public static Integer minOf(String raw) {
        if (raw == null) return null;
        Matcher range = RANGE.matcher(raw);
        if (range.find()) return Integer.valueOf(range.group(1));
        Matcher single = SINGLE.matcher(raw);
        return single.find() ? Integer.valueOf(single.group(1)) : null;
    }

    /** Upper bound of a range, or the single number, or null when the text has no digits. */
    public static Integer maxOf(String raw) {
        if (raw == null) return null;
        Matcher range = RANGE.matcher(raw);
        if (range.find()) return Integer.valueOf(range.group(2));
        Matcher single = SINGLE.matcher(raw);
        return single.find() ? Integer.valueOf(single.group(1)) : null;
    }

    /**
     * Validity expressed in days. "12 months" becomes 365, "6-12 months" takes the lower
     * bound at 182, and open-ended text such as "Project duration" returns null.
     */
    public static Integer validityDays(String raw, boolean upperBound) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.toLowerCase();
        Integer value = upperBound ? maxOf(raw) : minOf(raw);
        if (value == null) return null;
        if (lower.contains("year")) return value * 365;
        if (lower.contains("month")) return value * 30;
        return value;
    }

    /**
     * A cure or test hold expressed as whole days. The seed writes these in mixed units and
     * with alternatives: "12-24 hours", "48 hours (some consultants 72)", "7 days
     * conventional, 3 days rapid-set". The lower bound of the first quantity wins, because
     * that is the hold the programme must respect at minimum, and hours round up to a day:
     * a 12-hour cure still costs the following shift.
     */
    public static Integer holdWorkingDays(String raw) {
        Integer value = minOf(raw);
        if (value == null) return null;
        String lower = raw.toLowerCase();
        int hourAt = lower.indexOf("hour");
        int dayAt = lower.indexOf("day");
        boolean inHours = hourAt >= 0 && (dayAt < 0 || hourAt < dayAt);
        if (!inHours) return Math.max(1, value);
        return Math.max(1, (int) Math.ceil(value / 24.0));
    }

    /** True for "Yes" and "Yes - refundable"; false for "No" and "Sometimes". */
    public static boolean isYes(String raw) {
        return raw != null && raw.trim().toLowerCase().startsWith("yes");
    }

    /** True only for "Sometimes", where the PRO must decide per project. */
    public static boolean isConditional(String raw) {
        return raw != null && raw.trim().toLowerCase().startsWith("sometime");
    }

    /** Treats "-", "n/a" and blank as absent, so the caller gets a clean null. */
    public static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "-".equals(trimmed) || "n/a".equalsIgnoreCase(trimmed)) return null;
        return trimmed;
    }

    /**
     * Pulls permit codes out of prose. The seed mixes them with narrative, as in
     * "P-COMM-REG" but also "Consultant drawings, P-COMM-NOC" and "All inspections passed, P-DCD-FINAL".
     */
    public static List<String> permitCodes(String raw) {
        return matchAll(PERMIT_CODE, raw);
    }

    /** Pulls document codes (D01, D02, ...) out of prose. */
    public static List<String> documentCodes(String raw) {
        return matchAll(DOC_CODE, raw);
    }

    /**
     * The narrative left over once permit codes are removed, so a prerequisite such as
     * "Owner authorisation" is not silently lost.
     */
    public static String prerequisiteNarrative(String raw) {
        String cleaned = trimToNull(raw);
        if (cleaned == null) return null;
        String withoutCodes = PERMIT_CODE.matcher(cleaned).replaceAll("").trim();
        withoutCodes = withoutCodes.replaceAll("(^[,\\s]+)|([,\\s]+$)", "").replaceAll(",\\s*,", ",");
        return withoutCodes.isEmpty() ? null : withoutCodes;
    }

    /** Splits a comma or slash separated list, dropping empties and duplicates. */
    public static List<String> splitList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String part : raw.split("[,;/]")) {
            String value = part.trim();
            if (!value.isEmpty() && !"-".equals(value) && seen.add(value.toLowerCase())) {
                out.add(value);
            }
        }
        return out;
    }

    /** Joins a list back into the comma-separated form used in the text columns. */
    public static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return String.join(",", values);
    }

    /**
     * Normalises a community name for matching: lower case, punctuation stripped,
     * whitespace collapsed. "Jumeirah Islands / Park" and "jumeirah islands park" agree.
     */
    public static String communityKey(String name) {
        if (name == null) return null;
        String key = name.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return key.isEmpty() ? null : key;
    }

    private static List<String> matchAll(Pattern pattern, String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        Matcher matcher = pattern.matcher(raw);
        Set<String> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            if (seen.add(matcher.group())) out.add(matcher.group());
        }
        return out;
    }
}
