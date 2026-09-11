package com.fitouts.approval.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fitouts.approval.application.PermitAuthorityResolver.InheritSource;
import com.fitouts.approval.application.PermitAuthorityResolver.Result;
import com.fitouts.approval.domain.Authority;
import com.fitouts.approval.domain.AuthorityType;
import com.fitouts.approval.domain.Jurisdiction;
import com.fitouts.approvalconfig.domain.ApprovalAuthority;
import com.fitouts.approvalconfig.domain.ApprovalPermitType;
import com.fitouts.approvalconfig.domain.PermitAuthorityMechanisms;
import com.fitouts.approvalconfig.domain.PermitAuthorityRoles;
import com.fitouts.approvalconfig.domain.PermitResolutionModes;

class PermitAuthorityResolverTest {

    private static final UUID DCD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEWA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADDC_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void unsetMechanismIsUnresolvedAndDoesNotGuess() {
        ApprovalPermitType permit = permit("P-NEW", null);

        Result result = PermitAuthorityResolver.resolve(
                permit, List.of(), jurisdiction(), "Dubai", runtime(), Map.of(), List.of(), Map.of());

        assertThat(result.unresolved()).isTrue();
        assertThat(result.authorities()).isEmpty();
        assertThat(result.warning()).contains("no authority resolution mechanism");
    }

    @Test
    void fixedUsesTheLinkedRowEvenWhenNotOnTheJurisdiction() {
        ApprovalPermitType permit = permit("P-DCD-NOC", PermitAuthorityMechanisms.FIXED);
        permit.setFixedAuthorityId(DCD_ID);

        Result result = PermitAuthorityResolver.resolve(
                permit, List.of(), jurisdiction(), "Dubai", runtime(), configById(), List.of(), Map.of());

        assertThat(result.unresolved()).isFalse();
        assertThat(result.authorities()).extracting(Authority::getCode).containsExactly("DCD");
    }

    @Test
    void jurisdictionMasterDeveloperUsesTheJurisdictionCode() {
        ApprovalPermitType permit = permit("P-COMM-NOC", PermitAuthorityMechanisms.JURISDICTION_MASTER_DEVELOPER);

        Result result = PermitAuthorityResolver.resolve(
                permit, List.of(), jurisdiction(), "Dubai", runtime(), Map.of(), List.of(), Map.of());

        assertThat(result.authorities()).extracting(Authority::getCode).containsExactly("EMR");
    }

    @Test
    void emirateUtilityRequiresExactlyOneMatch() {
        ApprovalPermitType permit = permit("P-LOAD", PermitAuthorityMechanisms.EMIRATE_UTILITY);
        List<ApprovalAuthority> config = List.of(dewa(), addc());

        Result one = PermitAuthorityResolver.resolve(
                permit, List.of(), jurisdiction(), "Dubai", runtime(), Map.of(), List.of(dewa()), Map.of());
        assertThat(one.unresolved()).isFalse();
        assertThat(one.authorities()).extracting(Authority::getCode).containsExactly("DEWA");

        Result many = PermitAuthorityResolver.resolve(
                permit, List.of(), jurisdiction(), "Dubai", runtime(), Map.of(), config, Map.of());
        assertThat(many.unresolved()).isTrue();
        assertThat(many.candidateCodes()).containsExactly("DEWA", "ADDC");
        assertThat(many.warning()).contains("data problem");

        Result none = PermitAuthorityResolver.resolve(
                permit, List.of(), jurisdiction(), "Sharjah", runtime(), Map.of(), List.of(dewa()), Map.of());
        assertThat(none.unresolved()).isTrue();
        assertThat(none.candidateCodes()).isEmpty();
    }

    @Test
    void inheritUsesTheNamedPermitNotThePrerequisiteChain() {
        ApprovalPermitType permit = permit("P-DEPOSIT", PermitAuthorityMechanisms.INHERIT_FROM_PERMIT);
        permit.setInheritAuthorityFromPermitCode("P-COMM-NOC");
        InheritSource source = new InheritSource(List.of(authority("EMR", AuthorityType.MASTER_DEVELOPER)), List.of(), false);

        Result result = PermitAuthorityResolver.resolve(
                permit, List.of(), jurisdiction(), "Dubai", runtime(), Map.of(), List.of(),
                Map.of("P-COMM-NOC", source));

        assertThat(result.authorities()).extracting(Authority::getCode).containsExactly("EMR");
    }

    @Test
    void unconfirmedMultiDoesNotApplyTheDefault() {
        ApprovalPermitType permit = permit("P-ACCESS", PermitAuthorityMechanisms.MULTI_AUTHORITY);
        permit.setResolutionMode(PermitResolutionModes.ANY_ONE_APPLIES);

        Result result = PermitAuthorityResolver.resolve(
                permit,
                List.of(PermitAuthorityRoles.MASTER_DEVELOPER, PermitAuthorityRoles.BUILDING_MANAGEMENT),
                jurisdiction(),
                "Dubai",
                runtime(),
                Map.of(),
                List.of(),
                Map.of());

        assertThat(result.unresolved()).isTrue();
        assertThat(result.candidateCodes()).containsExactly("EMR", "OAFM");
        assertThat(result.warning()).contains("not been confirmed");
    }

    @Test
    void confirmedAnyOneWithTwoCandidatesStaysUnresolved() {
        ApprovalPermitType permit = permit("P-ACCESS", PermitAuthorityMechanisms.MULTI_AUTHORITY);
        permit.setResolutionMode(PermitResolutionModes.ANY_ONE_APPLIES);
        permit.setResolutionModeConfirmedBy("Ada");
        permit.setResolutionModeConfirmedAt(LocalDateTime.now());

        Result result = PermitAuthorityResolver.resolve(
                permit,
                List.of(PermitAuthorityRoles.MASTER_DEVELOPER, PermitAuthorityRoles.BUILDING_MANAGEMENT),
                jurisdiction(),
                "Dubai",
                runtime(),
                Map.of(),
                List.of(),
                Map.of());

        assertThat(result.unresolved()).isTrue();
        assertThat(result.candidateCodes()).containsExactly("EMR", "OAFM");
        assertThat(result.warning()).contains("manual authority selection");
    }

    @Test
    void confirmedAllRequiredEmitsOneAuthorityPerResolvedCandidate() {
        ApprovalPermitType permit = permit("P-ACCESS", PermitAuthorityMechanisms.MULTI_AUTHORITY);
        permit.setResolutionMode(PermitResolutionModes.ALL_REQUIRED);
        permit.setResolutionModeConfirmedBy("Ada");
        permit.setResolutionModeConfirmedAt(LocalDateTime.now());

        Result result = PermitAuthorityResolver.resolve(
                permit,
                List.of(PermitAuthorityRoles.MASTER_DEVELOPER, PermitAuthorityRoles.BUILDING_MANAGEMENT),
                jurisdiction(),
                "Dubai",
                runtime(),
                Map.of(),
                List.of(),
                Map.of());

        assertThat(result.unresolved()).isFalse();
        assertThat(result.authorities()).extracting(Authority::getCode).containsExactly("EMR", "OAFM");
    }

    @Test
    void caseIdentityTreatsNullAuthorityAsItsOwnSlot() {
        assertThat(ApprovalCaseService.caseIdentity("P-ACCESS", null))
                .isEqualTo(ApprovalCaseService.caseIdentity("P-ACCESS", ""));
        assertThat(ApprovalCaseService.caseIdentity("P-ACCESS", "EMR"))
                .isNotEqualTo(ApprovalCaseService.caseIdentity("P-ACCESS", "OAFM"));
    }

    private static ApprovalPermitType permit(String code, String mechanism) {
        ApprovalPermitType permit = new ApprovalPermitType();
        permit.setPermitCode(code);
        permit.setAuthorityResolutionMechanism(mechanism);
        permit.setResolutionMode(PermitResolutionModes.ANY_ONE_APPLIES);
        return permit;
    }

    private static Jurisdiction jurisdiction() {
        Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setMasterDeveloperAuthorityCode("EMR");
        jurisdiction.setBuildingManagementAuthorityCode("OAFM");
        jurisdiction.setRegulatorAuthorityCode("DM");
        jurisdiction.setEmirate("Dubai");
        return jurisdiction;
    }

    private static Map<String, Authority> runtime() {
        return Map.of(
                "DCD", authority("DCD", AuthorityType.SPECIAL),
                "EMR", authority("EMR", AuthorityType.MASTER_DEVELOPER),
                "OAFM", authority("OAFM", AuthorityType.BUILDING_MANAGEMENT),
                "DM", authority("DM", AuthorityType.REGULATOR),
                "DEWA", authority("DEWA", AuthorityType.UTILITY),
                "ADDC", authority("ADDC", AuthorityType.UTILITY));
    }

    private static Map<UUID, ApprovalAuthority> configById() {
        return Map.of(DCD_ID, config("DCD", "Regulator", "Dubai", true));
    }

    private static ApprovalAuthority dewa() {
        ApprovalAuthority authority = config("DEWA", "Utility", "Dubai", true);
        authority.setId(DEWA_ID);
        return authority;
    }

    private static ApprovalAuthority addc() {
        ApprovalAuthority authority = config("ADDC", "Utility", "Dubai", true);
        authority.setId(ADDC_ID);
        return authority;
    }

    private static Authority authority(String code, AuthorityType type) {
        Authority authority = new Authority();
        authority.setCode(code);
        authority.setName(code);
        authority.setType(type);
        return authority;
    }

    private static ApprovalAuthority config(String code, String type, String emirate, boolean emirateWide) {
        ApprovalAuthority authority = new ApprovalAuthority();
        authority.setCode(code);
        authority.setName(code);
        authority.setType(type);
        authority.setEmirate(emirate);
        authority.setAppliesEmirateWide(emirateWide);
        authority.setActive(true);
        authority.setDeleted(false);
        return authority;
    }
}
