package com.fitouts.approval.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fitouts.approval.api.ProjectScopeToggles;
import com.fitouts.boq.domain.BoqLine;

class ScopeFromWorkItemsTest {

    @Test
    void emptyCodesDoNotTurnOnDemolitionOrMep() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of());

        assertThat(scope.isDemolition()).isFalse();
        assertThat(scope.isMepLoadChange()).isFalse();
        assertThat(scope.isFireSystem()).isFalse();
    }

    @Test
    void kitchenTagLightsKitchenToggleOnly() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of("KITCHEN"));

        assertThat(scope.isCommercialKitchen()).isTrue();
        assertThat(scope.isDemolition()).isFalse();
        assertThat(scope.isSecuritySystem()).isFalse();
    }

    @Test
    void demolitionTagLightsDemolitionToggle() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of("DEMOLITION"));

        assertThat(scope.isDemolition()).isTrue();
        assertThat(scope.isSecuritySystem()).isFalse();
    }

    @Test
    void layoutAndStructuralBothCountAsStructuralChange() {
        assertThat(ScopeFromWorkItems.fromCodes(Set.of("LAYOUT")).isStructuralChange()).isTrue();
        assertThat(ScopeFromWorkItems.fromCodes(Set.of("STRUCTURAL")).isStructuralChange()).isTrue();
    }

    @Test
    void remainingCatalogCodesMapOntoToggles() {
        ProjectScopeToggles scope = ScopeFromWorkItems.fromCodes(Set.of(
                "FACADE", "MEP_LOAD", "FIRE_LIFE", "SIGNAGE", "SECURITY", "NIGHT", "HOARDING"));

        assertThat(scope.isFacadeChange()).isTrue();
        assertThat(scope.isMepLoadChange()).isTrue();
        assertThat(scope.isFireSystem()).isTrue();
        assertThat(scope.isSignage()).isTrue();
        assertThat(scope.isSecuritySystem()).isTrue();
        assertThat(scope.isNightWork()).isTrue();
        assertThat(scope.isHoardingOnRoad()).isTrue();
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
