package com.fitouts.approval.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.fitouts.approval.api.ProjectScopeToggles;

/**
 * Decides whether a permit applies to a project.
 *
 * <p>The seed states each permit's trigger as prose ("Any change to layout, structure, facade
 * or MEP capacity"), which a person can read but the resolver cannot. This translates the
 * catalogue's trigger column into scope-toggle predicates, so a painting job does not generate
 * a structural modification permit and a villa with no fire scope does not generate a Civil
 * Defence NOC.
 *
 * <p>A permit type may override its entry by storing JSON in {@code permit_type.trigger_rule_json};
 * anything not listed here defaults to applying whenever its issuing authority is in scope.
 */
public final class PermitTriggerRules {

    /** Permits that are operational rather than project-level and are never auto-generated. */
    private static final Map<String, String> NEVER_AUTO_GENERATE = Map.of(
            "P-HOT", "Issued per shift by site HSE, not once per project",
            "P-DEPOSIT", "Created automatically when a deposit-bearing permit is approved");

    private static final Map<String, Trigger> TRIGGERS = new LinkedHashMap<>();

    /** A rule plus the sentence shown to the PM explaining why the case appeared. */
    public record Trigger(Predicate<Context> applies, String reason) {
    }

    /** Everything the rules can look at. */
    public record Context(ProjectScopeToggles scope, boolean hasBuilding, boolean newBuild) {
    }

    static {
        rule("P-COMM-REG", c -> true,
                "Required once per community before any work by this contractor");
        rule("P-COMM-NOC", c -> true,
                "Any construction, renovation or fit-out in a managed community");
        rule("P-BLDG-NOC", Context::hasBuilding,
                "Unit fit-out inside a managed tower");
        rule("P-ACCESS", c -> true,
                "Workers and vehicles need passes before entering");
        rule("P-DEMO", c -> c.scope().isDemolition(),
                "Demolition or full strip-out is in scope");
        rule("P-MOD", c -> c.scope().isStructuralChange() || c.scope().isMepLoadChange()
                        || c.scope().isFacadeChange(),
                "Layout, structure, facade or MEP capacity is changing");
        rule("P-FITOUT", c -> !c.newBuild(),
                "Fit-out works inside an existing shell");
        rule("P-DCD-NOC", c -> c.scope().isFireSystem(),
                "Fire detection, suppression or fire-rated construction is in scope");
        rule("P-DCD-FINAL", c -> c.scope().isFireSystem(),
                "Civil Defence must inspect before the completion certificate");
        rule("P-DEWA-TEMP", c -> c.newBuild(),
                "Site power is needed for new build works");
        rule("P-DISCONNECT", c -> c.scope().isDemolition(),
                "Utilities must be disconnected before demolition");
        rule("P-RECONNECT", c -> c.scope().isDemolition() || c.scope().isMepLoadChange(),
                "Reconnection is needed before testing and handover");
        rule("P-LOAD", c -> c.scope().isMepLoadChange(),
                "Connected load exceeds the existing capacity");
        rule("P-SIRA", c -> c.scope().isSecuritySystem(),
                "CCTV or access control installation is in scope");
        rule("P-RTA", c -> c.scope().isHoardingOnRoad(),
                "Hoarding, skip, crane or lifting on public right of way");
        rule("P-WASTE", c -> c.scope().isDemolition(),
                "Waste is leaving site");
        rule("P-NIGHT", c -> c.scope().isNightWork(),
                "Work is planned outside standard permitted hours");
        rule("P-LIFT", Context::hasBuilding,
                "Material delivery in an occupied tower needs a booked lift");
        rule("P-SIGN", c -> c.scope().isSignage(),
                "External signage or fascia change is in scope");
        rule("P-KITCHEN", c -> c.scope().isCommercialKitchen(),
                "Commercial kitchen fit-out is in scope");
        rule("P-GREEN", Context::newBuild,
                "New build and major refurbishment need green building compliance");
        rule("P-COMPLETE", c -> true,
                "Completion certificate is required at the end of works");
    }

    private PermitTriggerRules() {
    }

    private static void rule(String code, Predicate<Context> applies, String reason) {
        TRIGGERS.put(code, new Trigger(applies, reason));
    }

    /** The reason a permit is never auto-generated, or null when it can be. */
    public static String excludedReason(String permitCode) {
        return NEVER_AUTO_GENERATE.get(permitCode);
    }

    /**
     * Whether this permit applies. Codes with no rule default to true, so a permit added to
     * the catalogue later still appears rather than silently vanishing.
     */
    public static boolean applies(String permitCode, Context context) {
        if (NEVER_AUTO_GENERATE.containsKey(permitCode)) return false;
        Trigger trigger = TRIGGERS.get(permitCode);
        return trigger == null || trigger.applies().test(context);
    }

    /** The sentence explaining why the case was generated. */
    public static String reason(String permitCode, String fallback) {
        Trigger trigger = TRIGGERS.get(permitCode);
        return trigger != null ? trigger.reason() : fallback;
    }
}
