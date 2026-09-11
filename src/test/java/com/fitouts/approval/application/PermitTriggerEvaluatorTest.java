package com.fitouts.approval.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.fitouts.approval.application.PermitTriggerEvaluator.Match;
import com.fitouts.approval.application.PermitTriggerEvaluator.PermitDef;
import com.fitouts.approvalconfig.domain.PermitTriggerTypes;

class PermitTriggerEvaluatorTest {

    private static final UUID APARTMENT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VILLA = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID NEW_BUILD = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FITOUT = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private static final Predicate<PermitDef> AUTHORITY_OK = def -> true;

    @Test
    void companyLevelPermitsAreNeverProjectCases() {
        PermitDef company = def("P-COMM-REG", PermitTriggerTypes.COMPANY);

        List<Match> matches = PermitTriggerEvaluator.select(
                List.of(company), Set.of(), null, null, AUTHORITY_OK);

        assertThat(matches).isEmpty();
    }

    @Test
    void locationOnlyNeedsAuthorityAndNothingElse() {
        PermitDef location = def("P-COMM-NOC", PermitTriggerTypes.LOCATION);

        assertThat(codes(PermitTriggerEvaluator.select(List.of(location), Set.of(), null, null, AUTHORITY_OK)))
                .containsExactly("P-COMM-NOC");
        assertThat(PermitTriggerEvaluator.select(List.of(location), Set.of(), null, null, def -> false))
                .isEmpty();
    }

    @Test
    void scopeTagIsOrAcrossLinkedTags() {
        PermitDef mod = new PermitDef(
                "P-MOD", "Modification", PermitTriggerTypes.SCOPE_TAG, null, null,
                Set.of("LAYOUT", "STRUCTURAL", "FACADE"), Set.of(), Set.of(), List.of());

        assertThat(codes(PermitTriggerEvaluator.select(List.of(mod), Set.of("LAYOUT"), null, null, AUTHORITY_OK)))
                .containsExactly("P-MOD");
        assertThat(codes(PermitTriggerEvaluator.select(List.of(mod), Set.of("FACADE"), null, null, AUTHORITY_OK)))
                .containsExactly("P-MOD");
        assertThat(PermitTriggerEvaluator.select(List.of(mod), Set.of("KITCHEN"), null, null, AUTHORITY_OK))
                .isEmpty();
        assertThat(PermitTriggerEvaluator.select(List.of(mod), Set.of(), null, null, AUTHORITY_OK))
                .isEmpty();
    }

    @Test
    void hotWorksScopeTagCanGenerateACase() {
        PermitDef hot = new PermitDef(
                "P-HOT", "Hot works", PermitTriggerTypes.SCOPE_TAG, null, null,
                Set.of("HOT_WORKS"), Set.of(), Set.of(), List.of());

        assertThat(codes(PermitTriggerEvaluator.select(List.of(hot), Set.of("HOT_WORKS"), null, null, AUTHORITY_OK)))
                .containsExactly("P-HOT");
    }

    @Test
    void propertyProjectMatchesOrWithinADimension() {
        PermitDef fitout = new PermitDef(
                "P-FITOUT", "Fit-out", PermitTriggerTypes.PROPERTY_PROJECT, null, null,
                Set.of(), Set.of(APARTMENT, UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")),
                Set.of(), List.of());

        assertThat(codes(PermitTriggerEvaluator.select(List.of(fitout), Set.of(), APARTMENT, null, AUTHORITY_OK)))
                .containsExactly("P-FITOUT");
        assertThat(PermitTriggerEvaluator.select(List.of(fitout), Set.of(), VILLA, null, AUTHORITY_OK))
                .isEmpty();
        assertThat(PermitTriggerEvaluator.select(List.of(fitout), Set.of(), null, null, AUTHORITY_OK))
                .isEmpty();
    }

    @Test
    void propertyProjectAndsAcrossLinkedDimensions() {
        PermitDef both = new PermitDef(
                "P-GREEN", "Green building", PermitTriggerTypes.PROPERTY_PROJECT, null, null,
                Set.of(), Set.of(VILLA), Set.of(NEW_BUILD), List.of());

        assertThat(PermitTriggerEvaluator.select(List.of(both), Set.of(), VILLA, FITOUT, AUTHORITY_OK))
                .isEmpty();
        assertThat(codes(PermitTriggerEvaluator.select(List.of(both), Set.of(), VILLA, NEW_BUILD, AUTHORITY_OK)))
                .containsExactly("P-GREEN");
    }

    @Test
    void prerequisiteWaitsUntilAllLinkedPermitsAreWanted() {
        PermitDef noc = def("P-DCD-NOC", PermitTriggerTypes.LOCATION);
        PermitDef finale = new PermitDef(
                "P-DCD-FINAL", "Final inspection", PermitTriggerTypes.PREREQUISITE, null, null,
                Set.of(), Set.of(), Set.of(), List.of("P-DCD-NOC"));
        PermitDef complete = new PermitDef(
                "P-COMPLETE", "Completion", PermitTriggerTypes.PREREQUISITE, null, null,
                Set.of(), Set.of(), Set.of(), List.of("P-DCD-FINAL"));

        List<Match> matches = PermitTriggerEvaluator.select(
                List.of(complete, finale, noc), Set.of(), null, null, AUTHORITY_OK);

        assertThat(codes(matches)).containsExactly("P-DCD-NOC", "P-DCD-FINAL", "P-COMPLETE");
    }

    @Test
    void prerequisiteWithNoLinkedCodesIsSkipped() {
        PermitDef empty = new PermitDef(
                "P-DEPOSIT", "Deposit", PermitTriggerTypes.PREREQUISITE, null, null,
                Set.of(), Set.of(), Set.of(), List.of());

        assertThat(PermitTriggerEvaluator.select(List.of(empty), Set.of(), null, null, AUTHORITY_OK))
                .isEmpty();
    }

    @Test
    void authorityGateAppliesToEveryTriggerType() {
        PermitDef location = def("P-ACCESS", PermitTriggerTypes.LOCATION);
        PermitDef scope = new PermitDef(
                "P-SIRA", "SIRA", PermitTriggerTypes.SCOPE_TAG, null, null,
                Set.of("SECURITY"), Set.of(), Set.of(), List.of());

        assertThat(PermitTriggerEvaluator.select(
                List.of(location, scope), Set.of("SECURITY"), null, null, def -> false))
                .isEmpty();
    }

    private static PermitDef def(String code, String trigger) {
        return new PermitDef(code, code, trigger, null, null, Set.of(), Set.of(), Set.of(), List.of());
    }

    private static List<String> codes(List<Match> matches) {
        return matches.stream().map(m -> m.permit().code()).toList();
    }
}
