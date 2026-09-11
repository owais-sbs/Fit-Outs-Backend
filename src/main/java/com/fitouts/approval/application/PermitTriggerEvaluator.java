package com.fitouts.approval.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.fitouts.approvalconfig.domain.PermitTriggerTypes;

/**
 * Live evaluation of Approvals Config permit rows against one project's resolved inputs.
 * There is no precomputed combination table: every catalogue row is tested each pass.
 */
public final class PermitTriggerEvaluator {

    private PermitTriggerEvaluator() {
    }

    public record PermitDef(
            String code,
            String name,
            String triggerType,
            String typicalTrigger,
            String issuingBody,
            Set<String> scopeTagCodes,
            Set<UUID> propertyTypeIds,
            Set<UUID> projectNatureIds,
            List<String> prerequisiteCodes
    ) {
        public PermitDef {
            scopeTagCodes = scopeTagCodes == null ? Set.of() : Set.copyOf(scopeTagCodes);
            propertyTypeIds = propertyTypeIds == null ? Set.of() : Set.copyOf(propertyTypeIds);
            projectNatureIds = projectNatureIds == null ? Set.of() : Set.copyOf(projectNatureIds);
            prerequisiteCodes = prerequisiteCodes == null ? List.of() : List.copyOf(prerequisiteCodes);
        }
    }

    public record Match(PermitDef permit, String reason) {
    }

    /**
     * Returns the permits that apply to this project, in catalogue order, with COMPANY excluded.
     * Prerequisite rows are included only once every linked permit code is already wanted.
     */
    public static List<Match> select(
            Collection<PermitDef> defs,
            Set<String> projectScopeTagCodes,
            UUID propertyTypeId,
            UUID projectNatureId,
            Predicate<PermitDef> authorityApplicable) {
        Set<String> tags = normalizeCodes(projectScopeTagCodes);
        Predicate<PermitDef> authorityOk = authorityApplicable == null ? def -> true : authorityApplicable;

        List<Match> out = new ArrayList<>();
        Set<String> wanted = new LinkedHashSet<>();
        List<PermitDef> pendingPrereq = new ArrayList<>();

        for (PermitDef def : defs) {
            if (def == null || def.code() == null || def.code().isBlank()) continue;
            String trigger = PermitTriggerTypes.normalize(def.triggerType());
            if (PermitTriggerTypes.COMPANY.equals(trigger)) continue;
            if (!authorityOk.test(def)) continue;

            if (PermitTriggerTypes.PREREQUISITE.equals(trigger)) {
                pendingPrereq.add(def);
                continue;
            }

            String reason = reasonIfApplies(def, trigger, tags, propertyTypeId, projectNatureId);
            if (reason == null) continue;
            wanted.add(def.code());
            out.add(new Match(def, reason));
        }

        boolean progressed = true;
        while (progressed && !pendingPrereq.isEmpty()) {
            progressed = false;
            List<PermitDef> remaining = new ArrayList<>();
            for (PermitDef def : pendingPrereq) {
                String reason = prerequisiteReason(def, wanted);
                if (reason == null) {
                    remaining.add(def);
                    continue;
                }
                wanted.add(def.code());
                out.add(new Match(def, reason));
                progressed = true;
            }
            pendingPrereq = remaining;
        }

        return out;
    }

    static String reasonIfApplies(PermitDef def, String trigger, Set<String> tags,
                                  UUID propertyTypeId, UUID projectNatureId) {
        return switch (trigger) {
            case PermitTriggerTypes.LOCATION -> "Applies because the issuing authority covers this project location";
            case PermitTriggerTypes.SCOPE_TAG -> scopeTagReason(def, tags);
            case PermitTriggerTypes.PROPERTY_PROJECT -> propertyProjectReason(def, propertyTypeId, projectNatureId);
            default -> scopeTagReason(def, tags);
        };
    }

    private static String scopeTagReason(PermitDef def, Set<String> tags) {
        if (def.scopeTagCodes().isEmpty()) return null;
        List<String> hit = new ArrayList<>();
        for (String code : def.scopeTagCodes()) {
            if (tags.contains(code)) hit.add(code);
        }
        if (hit.isEmpty()) return null;
        String fallback = def.typicalTrigger();
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "Triggered by scope tag" + (hit.size() == 1 ? " " : "s ") + String.join(", ", hit)
                + " on this project's BOQ";
    }

    private static String propertyProjectReason(PermitDef def, UUID propertyTypeId, UUID projectNatureId) {
        boolean linksProperty = !def.propertyTypeIds().isEmpty();
        boolean linksNature = !def.projectNatureIds().isEmpty();
        if (!linksProperty && !linksNature) return null;

        if (linksProperty) {
            if (propertyTypeId == null || !def.propertyTypeIds().contains(propertyTypeId)) return null;
        }
        if (linksNature) {
            if (projectNatureId == null || !def.projectNatureIds().contains(projectNatureId)) return null;
        }

        String fallback = def.typicalTrigger();
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "Matches this project's property type / project nature";
    }

    private static String prerequisiteReason(PermitDef def, Set<String> wanted) {
        if (def.prerequisiteCodes().isEmpty()) return null;
        for (String code : def.prerequisiteCodes()) {
            if (!wanted.contains(code)) return null;
        }
        return "Follows " + String.join(", ", def.prerequisiteCodes());
    }

    static Set<String> normalizeCodes(Collection<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        if (codes == null) return out;
        for (String raw : codes) {
            if (raw == null || raw.isBlank()) continue;
            out.add(raw.trim().toUpperCase(Locale.ROOT));
        }
        return out;
    }
}
