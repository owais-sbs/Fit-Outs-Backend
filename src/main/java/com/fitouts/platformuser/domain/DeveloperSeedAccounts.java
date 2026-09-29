package com.fitouts.platformuser.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Core migration-seeded demo accounts (V15, V17, V77).
 * Excludes ad-hoc {@code vendor.*@fitouts.demo} invites created at runtime.
 */
public final class DeveloperSeedAccounts {

    private static final Set<String> CORE_SEED_EMAILS = Set.of(
            "superadmin@fitouts.demo",
            "admin@fitouts.demo",
            "qs@fitouts.demo",
            "seniorqs@fitouts.demo",
            "pm@fitouts.demo",
            "director@fitouts.demo",
            "client@fitouts.demo",
            "designer@fitouts.demo",
            "qas@fitouts.demo",
            "finance@fitouts.demo",
            "sales@fitouts.demo",
            "employee@fitouts.demo",
            "subcontractor@fitouts.demo",
            "siteengineer@fitouts.demo",
            "estimator@fitouts.demo",
            "supervisor@fitouts.demo",
            "scqs@fitouts.demo",
            "doccontroller@fitouts.demo");

    private DeveloperSeedAccounts() {
    }

    public static boolean isCoreSeedEmail(String email) {
        return email != null && CORE_SEED_EMAILS.contains(email.toLowerCase(Locale.ROOT));
    }

    public static Set<String> coreSeedEmails() {
        return CORE_SEED_EMAILS;
    }
}
