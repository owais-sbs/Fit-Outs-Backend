package com.fitouts.approval.api;

import lombok.Getter;
import lombok.Setter;

/**
 * The scope questions that decide which authorities a project actually touches.
 * A villa with no structural change and no fire system does not need a structural
 * modification permit or a Civil Defence design NOC.
 */
@Getter
@Setter
public class ProjectScopeToggles {

    private boolean structuralChange;
    private boolean mepLoadChange;
    private boolean fireSystem;
    private boolean facadeChange;
    private boolean commercialKitchen;
    private boolean signage;
    private boolean nightWork;
    private boolean hoardingOnRoad;
    private boolean demolition;
    private boolean securitySystem;
    private boolean swimmingPool;
    private boolean landscape;

    /** Applied to a fit-out or renovation when the caller sends nothing. */
    public static ProjectScopeToggles defaults() {
        ProjectScopeToggles toggles = new ProjectScopeToggles();
        toggles.demolition = true;
        toggles.mepLoadChange = true;
        return toggles;
    }
}
