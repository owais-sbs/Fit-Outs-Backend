package com.fitouts.approval.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.approval.api.ResolvedAuthorityView;
import com.fitouts.approval.domain.Authority;
import com.fitouts.approval.domain.AuthorityRepository;
import com.fitouts.approval.domain.AuthorityType;
import com.fitouts.approval.domain.Jurisdiction;
import com.fitouts.approval.domain.JurisdictionRepository;
import com.fitouts.approvalconfig.domain.ApprovalAuthority;
import com.fitouts.approvalconfig.domain.ApprovalAuthorityRepository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Works out which authorities own a project.
 *
 * <p>Community lookup supplies the master developer, regulator and building-management
 * bodies. Emirate-wide authorities (Civil Defence, utilities, RTA-type) are added from the
 * Authority library when their flag is on — they do not need a jurisdiction row.
 */
@Service
@RequiredArgsConstructor
public class JurisdictionResolver {

    private final JurisdictionRepository jurisdictionRepository;
    private final AuthorityRepository authorityRepository;
    private final ApprovalAuthorityRepository approvalAuthorityRepository;

    @Getter
    public static class Resolution {
        private final List<ResolvedAuthorityView> authorities = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Set<String> codes = new LinkedHashSet<>();
        private Jurisdiction matched;
        private boolean jurisdictionMatched;
        private boolean jurisdictionUnverified;
        private String matchNote;

        void add(Authority authority, String layer, String reason) {
            if (authority == null || !codes.add(authority.getCode())) return;
            authorities.add(ResolvedAuthorityView.builder()
                    .code(authority.getCode())
                    .name(authority.getName())
                    .type(authority.getType().name())
                    .layer(layer)
                    .reason(reason)
                    .build());
        }

        public boolean hasAuthority(String code) {
            return codes.contains(code);
        }

        public Optional<ResolvedAuthorityView> byType(AuthorityType type) {
            return authorities.stream()
                    .filter(a -> type.name().equals(a.getType()))
                    .findFirst();
        }
    }

    /**
     * Resolves the applicable authority set.
     *
     * @param companyId     tenant, so tenant-added communities take precedence over the seed
     * @param emirate       project emirate, defaulted to Dubai when blank
     * @param communityName the community as entered on the project
     * @param buildingName  optional tower or building
     */
    @Transactional(readOnly = true)
    public Resolution resolve(UUID companyId, String emirate, String communityName,
                              String buildingName) {
        Resolution resolution = new Resolution();
        Map<String, Authority> authorities = authorityIndex(companyId);
        String resolvedEmirate = (emirate == null || emirate.isBlank()) ? "Dubai" : emirate.trim();

        Jurisdiction jurisdiction = findJurisdiction(companyId, communityName);
        if (jurisdiction != null) {
            resolution.matched = jurisdiction;
            resolution.jurisdictionMatched = true;
            resolution.jurisdictionUnverified = !jurisdiction.isVerified();
            resolution.matchNote = "Matched community \"" + jurisdiction.getCommunityName() + "\"";
            if (!jurisdiction.isVerified()) {
                resolution.warnings.add("The community record for \"" + jurisdiction.getCommunityName()
                        + "\" was derived from authority coverage text and has not been confirmed. "
                        + "Check the authority list before submitting anything.");
            }
            resolvedEmirate = jurisdiction.getEmirate() != null ? jurisdiction.getEmirate() : resolvedEmirate;

            resolution.add(authorities.get(jurisdiction.getRegulatorAuthorityCode()),
                    "Regulator", "Regulator for " + jurisdiction.getCommunityName());
            resolution.add(authorities.get(jurisdiction.getMasterDeveloperAuthorityCode()),
                    "Community", "Master developer for " + jurisdiction.getCommunityName());
            resolution.add(authorities.get(jurisdiction.getBuildingManagementAuthorityCode()),
                    "Building", "Building management for " + jurisdiction.getCommunityName());
            resolution.add(authorities.get(jurisdiction.getUtilityAuthorityCode()),
                    "Utility", "Utility for " + resolvedEmirate);
            for (String extra : SeedValueParser.splitList(jurisdiction.getAdditionalAuthorityCodes())) {
                resolution.add(authorities.get(extra), "Additional",
                        "Also covers " + jurisdiction.getCommunityName());
            }
        } else {
            resolution.jurisdictionMatched = false;
            if (communityName == null || communityName.isBlank()) {
                resolution.matchNote = "No community set on the project";
                resolution.warnings.add("Set the project's community so the correct master developer "
                        + "and regulator can be resolved. Without it only emirate-wide bodies apply.");
            } else {
                resolution.matchNote = "No match for \"" + communityName + "\"";
                resolution.warnings.add("\"" + communityName + "\" is not in the community library. "
                        + "Add it under Approvals then Authority library so cases generate correctly.");
            }
            // Fall back to the emirate's regulator and utility so the case set is not empty.
            fallbackByEmirate(resolution, authorities, resolvedEmirate);
        }

        // A tower unit is governed by its owners association on top of the community.
        if (buildingName != null && !buildingName.isBlank()
                && resolution.byType(AuthorityType.BUILDING_MANAGEMENT).isEmpty()) {
            resolution.add(authorities.get("OAFM"), "Building",
                    "Building management for " + buildingName.trim());
        }

        addEmirateWideAuthorities(resolution, authorities, companyId, resolvedEmirate);
        return resolution;
    }

    /**
     * Bodies that apply everywhere in the emirate (Civil Defence, utilities, RTA-type).
     * Flagged on the Authority library; no jurisdiction row is required.
     */
    private void addEmirateWideAuthorities(Resolution resolution, Map<String, Authority> authorities,
                                           UUID companyId, String emirate) {
        if (companyId == null) return;
        for (ApprovalAuthority config : approvalAuthorityRepository
                .findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId)) {
            if (!config.isActive() || !config.isAppliesEmirateWide()) continue;
            if (!sameEmirate(emirate, config.getEmirate())) continue;
            Authority runtime = authorities.get(config.getCode());
            if (runtime == null) continue;
            resolution.add(runtime, "Emirate",
                    runtime.getName() + " applies across " + emirate);
        }
    }

    private static boolean sameEmirate(String project, String authority) {
        if (project == null || project.isBlank() || authority == null || authority.isBlank()) {
            return false;
        }
        return project.trim().equalsIgnoreCase(authority.trim());
    }

    private void fallbackByEmirate(Resolution resolution, Map<String, Authority> authorities, String emirate) {
        switch (emirate.toLowerCase(Locale.ROOT)) {
            case "abu dhabi" -> {
                resolution.add(authorities.get("ADM"), "Regulator", "Abu Dhabi mainland regulator");
                resolution.add(authorities.get("ADDC"), "Utility", "Abu Dhabi utility");
            }
            case "sharjah" -> {
                resolution.add(authorities.get("SHJM"), "Regulator", "Sharjah regulator");
                resolution.add(authorities.get("SEWA"), "Utility", "Sharjah utility");
            }
            default -> {
                resolution.add(authorities.get("DM"), "Regulator",
                        "Default Dubai regulator; confirm the plot is not in a special development zone");
                resolution.add(authorities.get("DEWA"), "Utility", "Dubai utility");
            }
        }
    }

    private Jurisdiction findJurisdiction(UUID companyId, String communityName) {
        String key = SeedValueParser.communityKey(communityName);
        if (key == null) return null;

        List<Jurisdiction> exact = jurisdictionRepository.findByCommunityKeyVisible(key, companyId);
        if (!exact.isEmpty()) {
            return exact.get(0);
        }

        // A near match catches "Palm Jumeirah Frond K" against "palm jumeirah".
        List<Jurisdiction> all = jurisdictionRepository.findVisible(companyId);
        Jurisdiction best = null;
        int bestLength = 0;
        for (Jurisdiction candidate : all) {
            String candidateKey = candidate.getCommunityKey();
            if (candidateKey == null || candidateKey.length() < 4) continue;
            if (key.contains(candidateKey) && candidateKey.length() > bestLength) {
                best = candidate;
                bestLength = candidateKey.length();
            }
        }
        return best;
    }

    private Map<String, Authority> authorityIndex(UUID companyId) {
        Map<String, Authority> index = new LinkedHashMap<>();
        for (Authority authority : authorityRepository.findVisible(companyId)) {
            index.merge(authority.getCode(), authority,
                    (existing, candidate) -> candidate.getCompanyId() != null ? candidate : existing);
        }
        return index;
    }
}
