package com.fitouts.auth.application;

import java.util.Locale;
import java.util.Set;

import com.fitouts.company.domain.Company;

/** Demo-only whitelist for the Puma portal switcher. */
final class DemoPortalAccounts {

    static final String TENANT_SLUG = "puma";

    private static final Set<String> EMAILS = Set.of(
            "kiran@puma.com",
            "director@fitouts.demo",
            "humaidmn.4910@gmail.com",
            "designer@fitouts.demo",
            "employee@fitouts.demo",
            "finance@fitouts.demo",
            "pm@fitouts.demo",
            "qas@fitouts.demo",
            "qs@fitouts.demo",
            "sales@fitouts.demo",
            "seniorqs@fitouts.demo",
            "siteengineer@fitouts.demo",
            "taragif875@hiredify.com",
            "dct@fitouts.demo",
            "moid@fitouts.demo",
            "nameera@fitouts.demo",
            "doccontroller@fitouts.demo",
            "estimator@fitouts.demo",
            "scqs@fitouts.demo",
            "supervisor@fitouts.demo");

    private DemoPortalAccounts() {
    }

    static boolean isAllowed(String email) {
        return email != null && EMAILS.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    static boolean isDemoTenant(Company company) {
        if (company == null) {
            return false;
        }
        String slug = company.getDomainSlug();
        if (slug != null && TENANT_SLUG.equalsIgnoreCase(slug.trim())) {
            return true;
        }
        String name = company.getCompanyName();
        return name != null && TENANT_SLUG.equalsIgnoreCase(name.trim());
    }
}
