package com.fitouts.approval.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fitouts.approval.api.ProjectScopeToggles;
import com.fitouts.boq.domain.BoqLine;

class ScopeFromWorkItemsTest {

    private static PermitTriggerRules.Context ctx(ProjectScopeToggles scope) {
        return new PermitTriggerRules.Context(scope, false, false);
    }

    @Test
    void emptyCodesDoNotTurnOnDemolitionOrMep() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of());

        assertThat(scope.isDemolition()).isFalse();
        assertThat(scope.isMepLoadChange()).isFalse();
        assertThat(PermitTriggerRules.applies("P-DEMO", ctx(scope))).isFalse();
        assertThat(PermitTriggerRules.applies("P-SIRA", ctx(scope))).isFalse();
        assertThat(PermitTriggerRules.applies("P-COMM-NOC", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-ACCESS", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-COMPLETE", ctx(scope))).isTrue();
    }

    @Test
    void kitchenTagLightsKitchenPermitOnly() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of("KITCHEN"));

        assertThat(scope.isCommercialKitchen()).isTrue();
        assertThat(scope.isDemolition()).isFalse();
        assertThat(PermitTriggerRules.applies("P-KITCHEN", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-DEMO", ctx(scope))).isFalse();
        assertThat(PermitTriggerRules.applies("P-SIRA", ctx(scope))).isFalse();
    }

    @Test
    void demolitionTagLightsDemoDisconnectAndWaste() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of("DEMOLITION"));

        assertThat(scope.isDemolition()).isTrue();
        assertThat(PermitTriggerRules.applies("P-DEMO", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-DISCONNECT", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-WASTE", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-SIRA", ctx(scope))).isFalse();
    }

    @Test
    void layoutAndStructuralBothCountAsStructuralChange() {
        assertThat(ScopeFromWorkItems.fromCodes(Set.of("LAYOUT")).isStructuralChange()).isTrue();
        assertThat(ScopeFromWorkItems.fromCodes(Set.of("STRUCTURAL")).isStructuralChange()).isTrue();
        assertThat(PermitTriggerRules.applies("P-MOD",
                ctx(ScopeFromWorkItems.fromCodes(Set.of("LAYOUT"))))).isTrue();
    }

    @Test
    void hotWorksNeverAutoGeneratesACase() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of("HOT_WORKS"));

        assertThat(scope.isDemolition()).isFalse();
        assertThat(PermitTriggerRules.applies("P-HOT", ctx(scope))).isFalse();
        assertThat(PermitTriggerRules.excludedReason("P-HOT")).isNotBlank();
    }

    @Test
    void remainingCatalogCodesMapOntoTriggerToggles() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of(
                "FACADE", "MEP_LOAD", "FIRE_LIFE", "SIGNAGE", "SECURITY", "NIGHT", "HOARDING"));

        assertThat(scope.isFacadeChange()).isTrue();
        assertThat(scope.isMepLoadChange()).isTrue();
        assertThat(scope.isFireSystem()).isTrue();
        assertThat(scope.isSignage()).isTrue();
        assertThat(scope.isSecuritySystem()).isTrue();
        assertThat(scope.isNightWork()).isTrue();
        assertThat(scope.isHoardingOnRoad()).isTrue();
        assertThat(PermitTriggerRules.applies("P-SIRA", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-NIGHT", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-RTA", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-DCD-NOC", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-LOAD", ctx(scope))).isTrue();
        assertThat(PermitTriggerRules.applies("P-SIGN", ctx(scope))).isTrue();
    }

    @Test
    void descriptionMatchingCatalogNameResolvesWorkItem() {
        UUID kitchenId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, UUID> byName = Map.of("supply and install kitchen cabinets", kitchenId);

        BoqLine line = new BoqLine();
        line.setDescription("Supply and install kitchen cabinets");

        assertThat(ScopeFromWorkItems.resolveWorkItemId(line, byName)).isEqualTo(kitchenId);
    }
}
