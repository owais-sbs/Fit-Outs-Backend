package com.fitouts.approval.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.fitouts.approval.domain.Authority;
import com.fitouts.approval.domain.AuthorityType;
import com.fitouts.approval.domain.Jurisdiction;
import com.fitouts.approvalconfig.domain.ApprovalAuthority;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.PermitAuthorityMechanisms;
import com.fitouts.approvalconfig.domain.PermitAuthorityRoles;
import com.fitouts.approvalconfig.domain.PermitResolutionModes;

/**
 * Picks the Authority (or authorities) that will issue one catalogue permit.
 *
 * <p>Never decides whether the permit applies — that stays with the trigger evaluator.
 * A miss, a collision, an unset mechanism, or an unconfirmed MULTI_AUTHORITY mode
 * all return an unresolved result so the case can still be created.
 */
public final class PermitAuthorityResolver {

    private PermitAuthorityResolver() {
    }

    public record InheritSource(List<Authority> authorities, List<String> candidateCodes, boolean unresolved) {
        public InheritSource {
            authorities = authorities == null ? List.of() : List.copyOf(authorities);
            candidateCodes = candidateCodes == null ? List.of() : List.copyOf(candidateCodes);
        }
    }

    public record Result(List<Authority> authorities, List<String> candidateCodes, String warning,
                         boolean unresolved) {
        public Result {
            authorities = authorities == null ? List.of() : List.copyOf(authorities);
            candidateCodes = candidateCodes == null ? List.of() : List.copyOf(candidateCodes);
        }

        public static Result bound(Authority authority) {
            return new Result(List.of(authority), List.of(), null, false);
        }

        public static Result boundAll(List<Authority> authorities) {
            return new Result(authorities, List.of(), null, false);
        }

        public static Result unresolved(List<String> candidates, String warning) {
            return new Result(List.of(), candidates, warning, true);
        }
    }

    public static Result resolve(
            ApprovalPermitType permit,
            List<String> candidateRoles,
            Jurisdiction jurisdiction,
            String emirate,
            Map<String, Authority> runtimeByCode,
            Map<java.util.UUID, ApprovalAuthority> configById,
            List<ApprovalAuthority> configAuthorities,
            Map<String, InheritSource> inheritByPermitCode) {
        if (permit == null) {
            return Result.unresolved(List.of(), "Permit type is missing");
        }
        String mechanism = PermitAuthorityMechanisms.normalize(permit.getAuthorityResolutionMechanism());
        String code = permit.getPermitCode();
        if (mechanism == null) {
            return Result.unresolved(List.of(),
                    code + " has no authority resolution mechanism. Set one on the Permit Catalogue.");
        }
        return switch (mechanism) {
            case PermitAuthorityMechanisms.FIXED -> resolveFixed(permit, runtimeByCode, configById);
            case PermitAuthorityMechanisms.JURISDICTION_MASTER_DEVELOPER ->
                    resolveJurisdictionRole(code, PermitAuthorityRoles.MASTER_DEVELOPER, jurisdiction, runtimeByCode);
            case PermitAuthorityMechanisms.JURISDICTION_BUILDING_MANAGEMENT ->
                    resolveJurisdictionRole(code, PermitAuthorityRoles.BUILDING_MANAGEMENT, jurisdiction, runtimeByCode);
            case PermitAuthorityMechanisms.JURISDICTION_REGULATOR ->
                    resolveJurisdictionRole(code, PermitAuthorityRoles.REGULATOR, jurisdiction, runtimeByCode);
            case PermitAuthorityMechanisms.EMIRATE_UTILITY ->
                    resolveEmirateUtility(code, emirate, runtimeByCode, configAuthorities);
            case PermitAuthorityMechanisms.INHERIT_FROM_PERMIT ->
                    resolveInherit(permit, inheritByPermitCode);
            case PermitAuthorityMechanisms.MULTI_AUTHORITY ->
                    resolveMulti(permit, candidateRoles, jurisdiction, runtimeByCode);
            default -> Result.unresolved(List.of(),
                    code + " has an unrecognised authority resolution mechanism.");
        };
    }

    /**
     * FIXED always uses the linked Authority row. Seeded FIXED permits are Dubai-named
     * bodies; there is no emirate switch — another emirate needs a new Permit Type.
     */
    private static Result resolveFixed(ApprovalPermitType permit, Map<String, Authority> runtimeByCode,
                                       Map<java.util.UUID, ApprovalAuthority> configById) {
        String code = permit.getPermitCode();
        if (permit.getFixedAuthorityId() == null) {
            return Result.unresolved(List.of(), code + " is FIXED but has no Authority linked.");
        }
        ApprovalAuthority config = configById != null ? configById.get(permit.getFixedAuthorityId()) : null;
        if (config == null || !StringUtils.hasText(config.getCode())) {
            return Result.unresolved(List.of(), code + " is FIXED but the linked Authority is missing.");
        }
        Authority runtime = runtimeByCode != null ? runtimeByCode.get(config.getCode()) : null;
        if (runtime == null) {
            return Result.unresolved(List.of(config.getCode()),
                    code + " is FIXED to " + config.getCode() + " but that Authority is not in the runtime library.");
        }
        return Result.bound(runtime);
    }

    private static Result resolveJurisdictionRole(String permitCode, String role, Jurisdiction jurisdiction,
                                                  Map<String, Authority> runtimeByCode) {
        if (jurisdiction == null) {
            return Result.unresolved(List.of(),
                    permitCode + " needs a matched Jurisdiction record to resolve the "
                            + PermitAuthorityRoles.label(role) + ".");
        }
        String authorityCode = jurisdictionCodeForRole(jurisdiction, role);
        if (!StringUtils.hasText(authorityCode)) {
            return Result.unresolved(List.of(),
                    permitCode + " has no " + PermitAuthorityRoles.label(role)
                            + " on the matched Jurisdiction record.");
        }
        Authority runtime = runtimeByCode != null ? runtimeByCode.get(authorityCode.trim()) : null;
        if (runtime == null) {
            return Result.unresolved(List.of(authorityCode.trim()),
                    permitCode + " resolved " + PermitAuthorityRoles.label(role) + " to "
                            + authorityCode.trim() + " but that Authority is not in the runtime library.");
        }
        return Result.bound(runtime);
    }

    private static Result resolveEmirateUtility(String permitCode, String emirate,
                                                Map<String, Authority> runtimeByCode,
                                                List<ApprovalAuthority> configAuthorities) {
        String resolvedEmirate = StringUtils.hasText(emirate) ? emirate.trim() : "Dubai";
        List<ApprovalAuthority> matches = new ArrayList<>();
        if (configAuthorities != null) {
            for (ApprovalAuthority config : configAuthorities) {
                if (config == null || !config.isActive() || config.isDeleted()) continue;
                if (!config.isAppliesEmirateWide()) continue;
                if (!isUtility(config.getType())) continue;
                if (!sameEmirate(resolvedEmirate, config.getEmirate())) continue;
                matches.add(config);
            }
        }
        List<String> codes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Authority> runtimeMatches = new ArrayList<>();
        for (ApprovalAuthority config : matches) {
            if (config.getCode() == null || !seen.add(config.getCode())) continue;
            codes.add(config.getCode());
            Authority runtime = runtimeByCode != null ? runtimeByCode.get(config.getCode()) : null;
            if (runtime != null) runtimeMatches.add(runtime);
        }
        if (runtimeMatches.size() == 1 && codes.size() == 1) {
            return Result.bound(runtimeMatches.get(0));
        }
        String warning;
        if (codes.isEmpty()) {
            warning = permitCode + " is EMIRATE_UTILITY but no Utility authority with applies_emirate_wide "
                    + "matches " + resolvedEmirate + ". This is a data problem.";
        } else if (codes.size() > 1) {
            warning = permitCode + " is EMIRATE_UTILITY but matched " + codes.size()
                    + " Utility authorities for " + resolvedEmirate + " (" + String.join(", ", codes)
                    + "). This is a data problem.";
        } else {
            warning = permitCode + " is EMIRATE_UTILITY and matched " + codes.get(0)
                    + " in config but that Authority is not in the runtime library.";
        }
        return Result.unresolved(codes, warning);
    }

    private static Result resolveInherit(ApprovalPermitType permit, Map<String, InheritSource> inheritByPermitCode) {
        String code = permit.getPermitCode();
        String sourceCode = permit.getInheritAuthorityFromPermitCode();
        if (!StringUtils.hasText(sourceCode)) {
            return Result.unresolved(List.of(),
                    code + " is INHERIT_FROM_PERMIT but inherit_authority_from_permit_code is empty.");
        }
        String key = sourceCode.trim().toUpperCase(Locale.ROOT);
        InheritSource source = inheritByPermitCode != null ? inheritByPermitCode.get(key) : null;
        if (source == null) {
            return Result.unresolved(List.of(),
                    code + " inherits authority from " + key + " but that permit has no case on this project yet.");
        }
        if (source.unresolved() || source.authorities().isEmpty()) {
            return Result.unresolved(source.candidateCodes(),
                    code + " inherits authority from " + key
                            + " but that permit's authority is not resolved.");
        }
        if (source.authorities().size() > 1) {
            List<String> codes = source.authorities().stream().map(Authority::getCode).toList();
            return Result.unresolved(codes,
                    code + " inherits authority from " + key
                            + " which resolved to more than one authority (" + String.join(", ", codes)
                            + "). Do not guess.");
        }
        return Result.bound(source.authorities().get(0));
    }

    private static Result resolveMulti(ApprovalPermitType permit, List<String> candidateRoles,
                                       Jurisdiction jurisdiction, Map<String, Authority> runtimeByCode) {
        String code = permit.getPermitCode();
        boolean confirmed = permit.getResolutionModeConfirmedAt() != null
                && StringUtils.hasText(permit.getResolutionModeConfirmedBy());
        Map<String, Authority> byCode = new LinkedHashMap<>();
        List<String> missingRoles = new ArrayList<>();
        if (candidateRoles != null) {
            for (String raw : candidateRoles) {
                String role = PermitAuthorityRoles.normalize(raw);
                if (role == null) continue;
                Result roleResult = resolveJurisdictionRole(code, role, jurisdiction, runtimeByCode);
                if (roleResult.unresolved() || roleResult.authorities().isEmpty()) {
                    missingRoles.add(PermitAuthorityRoles.label(role));
                    continue;
                }
                Authority authority = roleResult.authorities().get(0);
                byCode.putIfAbsent(authority.getCode(), authority);
            }
        }
        List<Authority> matched = new ArrayList<>(byCode.values());
        List<String> candidateCodes = matched.stream().map(Authority::getCode).toList();

        if (!confirmed) {
            String warning = code + " is MULTI_AUTHORITY but the resolution mode has not been confirmed. "
                    + "The default ANY_ONE_APPLIES is not applied until an admin confirms it.";
            return Result.unresolved(candidateCodes, warning);
        }

        String mode = PermitResolutionModes.normalize(permit.getResolutionMode());
        if (PermitResolutionModes.ALL_REQUIRED.equals(mode)) {
            if (matched.isEmpty()) {
                String extra = missingRoles.isEmpty() ? ""
                        : " Missing roles: " + String.join(", ", missingRoles) + ".";
                return Result.unresolved(candidateCodes,
                        code + " is ALL_REQUIRED but no candidate authority resolved on this plot." + extra);
            }
            return Result.boundAll(matched);
        }

        if (matched.size() == 1) {
            return Result.bound(matched.get(0));
        }
        String warning;
        if (matched.isEmpty()) {
            warning = code + " is ANY_ONE_APPLIES but no candidate authority resolved on this plot.";
        } else {
            warning = code + " is ANY_ONE_APPLIES but more than one candidate exists ("
                    + String.join(", ", candidateCodes)
                    + "). Flag for manual authority selection rather than picking one.";
        }
        return Result.unresolved(candidateCodes, warning);
    }

    static String jurisdictionCodeForRole(Jurisdiction jurisdiction, String role) {
        if (jurisdiction == null) return null;
        String normalised = PermitAuthorityRoles.normalize(role);
        if (PermitAuthorityRoles.MASTER_DEVELOPER.equals(normalised)) {
            return jurisdiction.getMasterDeveloperAuthorityCode();
        }
        if (PermitAuthorityRoles.BUILDING_MANAGEMENT.equals(normalised)) {
            return jurisdiction.getBuildingManagementAuthorityCode();
        }
        if (PermitAuthorityRoles.REGULATOR.equals(normalised)) {
            return jurisdiction.getRegulatorAuthorityCode();
        }
        return null;
    }

    private static boolean isUtility(String type) {
        return AuthorityType.fromLabel(type) == AuthorityType.UTILITY;
    }

    private static boolean sameEmirate(String project, String authority) {
        if (project == null || project.isBlank() || authority == null || authority.isBlank()) {
            return false;
        }
        return project.trim().equalsIgnoreCase(authority.trim());
    }
}
