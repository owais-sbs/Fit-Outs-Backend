package com.fitouts.approval.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fitouts.approval.domain.Authority;
import com.fitouts.approval.domain.AuthorityType;
import com.fitouts.approval.domain.Jurisdiction;

/**
 * Builds the resolver's community-to-authority lookup.
 *
 * <p>The seed file has no jurisdiction table. Coverage exists only as a prose sentence on
 * each authority, for example Nakheel's "Palm Jumeirah, Jumeirah Park, Jumeirah Islands,
 * Jumeirah Heights, Al Furjan, The Gardens, Nad Al Sheba, Warsan". This class splits those
 * sentences into rows and merges the layers, so Palm Jumeirah correctly resolves to both
 * Trakhees (regulator) and Nakheel (master developer).
 *
 * <p>Every row it produces is marked unverified. The parsing is good enough to be useful and
 * not good enough to be trusted without a human reading it.
 */
public class JurisdictionDeriver {

    /** Splits on commas that sit outside parentheses, so "Emirates Living (Springs, Meadows)" stays whole. */
    private static final Pattern TOP_LEVEL_SPLIT = Pattern.compile(",(?![^(]*\\))");
    private static final Pattern NUMBER_RANGE = Pattern.compile("^(.*?)\\s+(\\d+)\\s*(?:-|–|to)\\s*(\\d+)$");
    private static final Pattern NUMBER_AND = Pattern.compile("^(.*?)\\s+(\\d+)\\s+and\\s+(\\d+)$");
    private static final Pattern PARENTHETICAL = Pattern.compile("^(.*?)\\s*\\((.*)\\)\\s*$");

    /**
     * Phrases that describe a coverage area rather than name a community. When a chunk matches
     * one of these, only its parenthetical contents are usable.
     */
    private static final List<String> DESCRIPTIVE_MARKERS = List.of(
            "all of", "mainland", "freehold plots", "special development zones", "right of way",
            "metro corridors", "public roads", "commercial premises", "apartment towers",
            "mixed-use buildings", "fire and life safety", "security systems", "and similar",
            "districts", "vicinity", "area", "clinics", "healthcare", "schools", "nurseries",
            "outlets", "zones:", "master developments");

    /** Fragments that are never a community on their own. */
    private static final Set<String> NOISE = Set.of(
            "etc", "and", "or", "the", "similar", "others", "loams", "kaizen", "deyaar fm",
            "farnek", "concordia", "idama", "savills", "dtec", "expo city vicinity");

    /**
     * Derives jurisdiction rows from the given authorities.
     *
     * @param authorities the full seeded authority list
     * @return one row per community, with the layers merged
     */
    public List<Jurisdiction> derive(List<Authority> authorities) {
        Map<String, AuthorityType> typeByCode = new LinkedHashMap<>();
        Map<String, String> emirateByCode = new LinkedHashMap<>();
        for (Authority authority : authorities) {
            typeByCode.put(authority.getCode(), authority.getType());
            emirateByCode.put(authority.getCode(), authority.getEmirate());
        }

        // A utility covering a whole emirate applies to every community in it rather than
        // producing community rows of its own.
        Map<String, String> utilityByEmirate = new LinkedHashMap<>();
        for (Authority authority : authorities) {
            if (authority.getType() == AuthorityType.UTILITY && authority.getEmirate() != null) {
                utilityByEmirate.putIfAbsent(normaliseEmirate(authority.getEmirate()), authority.getCode());
            }
        }

        Map<String, Jurisdiction> byKey = new LinkedHashMap<>();
        for (Authority authority : authorities) {
            if (authority.getType() == AuthorityType.UTILITY) {
                continue;
            }
            String emirate = authority.getEmirate() != null ? authority.getEmirate() : "Dubai";
            for (String community : extractCommunities(authority.getJurisdictionAreas())) {
                String key = SeedValueParser.communityKey(community);
                if (key == null) continue;

                Jurisdiction row = byKey.computeIfAbsent(key, k -> {
                    Jurisdiction created = new Jurisdiction();
                    created.setEmirate(emirate);
                    created.setCommunityName(community);
                    created.setCommunityKey(k);
                    created.setVerified(false);
                    created.setUtilityAuthorityCode(utilityByEmirate.get(normaliseEmirate(emirate)));
                    return created;
                });

                assignLayer(row, authority);
                appendDerivedFrom(row, authority.getCode());
            }
        }

        return new ArrayList<>(byKey.values());
    }

    /**
     * Puts an authority into the correct layer slot. When two authorities of the same layer
     * claim one community, the first wins and the second is recorded as additional rather
     * than dropped, because that overlap is usually a real signal.
     */
    private void assignLayer(Jurisdiction row, Authority authority) {
        String code = authority.getCode();
        switch (authority.getType()) {
            case REGULATOR -> {
                if (row.getRegulatorAuthorityCode() == null) {
                    row.setRegulatorAuthorityCode(code);
                } else {
                    addAdditional(row, code);
                }
            }
            case MASTER_DEVELOPER -> {
                if (row.getMasterDeveloperAuthorityCode() == null) {
                    row.setMasterDeveloperAuthorityCode(code);
                } else {
                    addAdditional(row, code);
                }
            }
            case BUILDING_MANAGEMENT -> {
                if (row.getBuildingManagementAuthorityCode() == null) {
                    row.setBuildingManagementAuthorityCode(code);
                } else {
                    addAdditional(row, code);
                }
            }
            default -> addAdditional(row, code);
        }
    }

    private void addAdditional(Jurisdiction row, String code) {
        Set<String> codes = new LinkedHashSet<>(SeedValueParser.splitList(row.getAdditionalAuthorityCodes()));
        codes.add(code);
        row.setAdditionalAuthorityCodes(String.join(",", codes));
    }

    private void appendDerivedFrom(Jurisdiction row, String code) {
        Set<String> sources = new LinkedHashSet<>(SeedValueParser.splitList(row.getDerivedFrom()));
        sources.add(code);
        String joined = String.join(",", sources);
        row.setDerivedFrom(joined.length() > 250 ? joined.substring(0, 250) : joined);
    }

    /**
     * Extracts community names from one authority's coverage sentence.
     * Handles parenthetical sub-lists, shared-prefix slash lists such as
     * "Jumeirah Islands / Park / Heights", and numbered ranges such as "Arabian Ranches 1-3".
     */
    List<String> extractCommunities(String areas) {
        List<String> out = new ArrayList<>();
        if (areas == null || areas.isBlank()) return out;

        String text = areas;
        int colon = text.indexOf(':');
        if (colon >= 0 && colon < 30) {
            // "TECOM zones: Dubai Internet City, ..." — the list starts after the colon.
            text = text.substring(colon + 1);
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String rawChunk : TOP_LEVEL_SPLIT.split(text)) {
            String chunk = rawChunk.trim();
            if (chunk.isEmpty()) continue;

            Matcher parenthetical = PARENTHETICAL.matcher(chunk);
            if (parenthetical.matches()) {
                String head = parenthetical.group(1).trim();
                String inner = parenthetical.group(2).trim();
                if (!isDescriptive(head)) {
                    addExpanded(out, seen, head);
                }
                for (String innerPart : inner.split(",")) {
                    addExpanded(out, seen, innerPart.trim());
                }
                continue;
            }

            if (isDescriptive(chunk)) continue;
            addExpanded(out, seen, chunk);
        }
        return out;
    }

    /**
     * Expands one chunk into the community names it stands for.
     * "Arabian Ranches 1-3" becomes three rows; "Jumeirah Islands / Park / Heights" becomes
     * three rows sharing the leading word.
     */
    private void addExpanded(List<String> out, Set<String> seen, String chunk) {
        if (chunk == null) return;
        String value = chunk.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return;

        if (value.contains("/")) {
            String[] parts = value.split("/");
            String prefix = "";
            String first = parts[0].trim();
            int lastSpace = first.lastIndexOf(' ');
            if (lastSpace > 0) {
                prefix = first.substring(0, lastSpace + 1);
            }
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) continue;
                addSingle(out, seen, i == 0 || part.contains(" ") ? part : prefix + part);
            }
            return;
        }

        Matcher range = NUMBER_RANGE.matcher(value);
        if (range.matches()) {
            String base = range.group(1).trim();
            int from = Integer.parseInt(range.group(2));
            int to = Integer.parseInt(range.group(3));
            if (to - from <= 10) {
                for (int i = from; i <= to; i++) {
                    addSingle(out, seen, base + " " + i);
                }
                return;
            }
        }

        Matcher and = NUMBER_AND.matcher(value);
        if (and.matches()) {
            String base = and.group(1).trim();
            addSingle(out, seen, base + " " + and.group(2));
            addSingle(out, seen, base + " " + and.group(3));
            return;
        }

        addSingle(out, seen, value);
    }

    private void addSingle(List<String> out, Set<String> seen, String value) {
        if (value == null) return;
        String cleaned = value.trim().replaceAll("^(and|the)\\s+", "").trim();
        if (cleaned.length() < 3 || cleaned.length() > 90) return;
        if (NOISE.contains(cleaned.toLowerCase(Locale.ROOT))) return;
        if (isDescriptive(cleaned)) return;
        if (!cleaned.matches(".*[A-Za-z].*")) return;
        if (seen.add(cleaned.toLowerCase(Locale.ROOT))) {
            out.add(cleaned);
        }
    }

    private boolean isDescriptive(String chunk) {
        String lower = chunk.toLowerCase(Locale.ROOT);
        for (String marker : DESCRIPTIVE_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    private String normaliseEmirate(String emirate) {
        return emirate == null ? "" : emirate.trim().toLowerCase(Locale.ROOT);
    }
}
