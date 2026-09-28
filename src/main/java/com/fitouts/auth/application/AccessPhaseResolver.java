package com.fitouts.auth.application;

import com.fitouts.account.domain.Account;
import com.fitouts.auth.domain.AccessPhase;
import com.fitouts.auth.domain.Role;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyStatus;

public final class AccessPhaseResolver {

    private AccessPhaseResolver() {
    }

    public static AccessPhase resolve(Account account) {
        if (account == null) {
            return AccessPhase.SUBSCRIBE;
        }
        if (account.getRoles() != null && account.getRoles().contains(Role.SUPER_ADMIN)) {
            return AccessPhase.PORTAL;
        }

        Company company = account.getCompany();
        if (company == null) {
            return isSelfServeAdmin(account) ? AccessPhase.SUBSCRIBE : AccessPhase.PORTAL;
        }

        CompanyStatus status = company.getStatus();
        if (status == CompanyStatus.ACTIVE) {
            if (Boolean.TRUE.equals(company.getOnboardingCompleted())) {
                return AccessPhase.PORTAL;
            }
            return isSelfServeAdmin(account) ? AccessPhase.ONBOARDING : AccessPhase.PORTAL;
        }

        if (status == CompanyStatus.TRIAL && isSelfServeAdmin(account)) {
            return AccessPhase.SUBSCRIBE;
        }

        // Non-admin staff on inactive tenants are blocked at login; treat as non-portal.
        return AccessPhase.SUBSCRIBE;
    }

    public static boolean isSelfServeAdmin(Account account) {
        return account.getRoles() != null
                && account.getRoles().contains(Role.ADMIN)
                && !account.getRoles().contains(Role.SUPER_ADMIN);
    }

    /**
     * Whether the account may establish a session.
     * Self-serve ADMINS may log in without an ACTIVE company (subscribe / onboarding).
     * All other non–super-admin users still require an ACTIVE company.
     */
    public static boolean isLoginAllowed(Account account) {
        if (account.getRoles() != null && account.getRoles().contains(Role.SUPER_ADMIN)) {
            return true;
        }
        Company company = account.getCompany();
        if (isSelfServeAdmin(account)) {
            if (company == null) {
                return true;
            }
            CompanyStatus status = company.getStatus();
            return status == CompanyStatus.ACTIVE || status == CompanyStatus.TRIAL;
        }
        return company != null && company.getStatus() == CompanyStatus.ACTIVE;
    }
}
