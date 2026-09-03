package com.fitouts.boq.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.enums.BoqApprovalStep;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.UnauthorizedException;

@Component
public class BoqAuthHelper {

    private static final Set<Role> SUBMIT_ROLES = Set.of(
            Role.QS, Role.SENIOR_QS, Role.ADMIN, Role.SUPER_ADMIN);

    public AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal;
    }

    public void requireSubmitRole(AuthPrincipal principal) {
        if (!hasAnyRole(principal, SUBMIT_ROLES)) {
            throw new ForbiddenException("Only QS can submit BOQ for approval");
        }
    }

    public void requireApproverForStatus(AuthPrincipal principal, BoqDocumentStatus status) {
        Role required = approverRoleForStatus(status);
        if (required == null) {
            throw new ForbiddenException("BOQ is not pending approval");
        }
        if (!hasRole(principal, required) && !hasRole(principal, Role.SUPER_ADMIN)) {
            throw new ForbiddenException("You are not authorized to act on this approval step");
        }
    }

    public BoqApprovalStep stepForStatus(BoqDocumentStatus status) {
        return switch (status) {
            case PENDING_SENIOR_QS -> BoqApprovalStep.SENIOR_QS;
            case PENDING_PM -> BoqApprovalStep.PM;
            case PENDING_DIRECTOR -> BoqApprovalStep.DIRECTOR;
            case PENDING_CLIENT -> BoqApprovalStep.CLIENT;
            default -> null;
        };
    }

    public Role approverRoleForStatus(BoqDocumentStatus status) {
        return switch (status) {
            case PENDING_SENIOR_QS -> Role.SENIOR_QS;
            case PENDING_PM -> Role.PROJECT_MANAGER;
            case PENDING_DIRECTOR -> Role.BUSINESS_OWNER;
            case PENDING_CLIENT -> Role.CLIENT;
            default -> null;
        };
    }

    public BoqDocumentStatus nextStatusAfterApproval(BoqDocumentStatus current) {
        return switch (current) {
            case PENDING_SENIOR_QS -> BoqDocumentStatus.PENDING_PM;
            case PENDING_PM -> BoqDocumentStatus.PENDING_DIRECTOR;
            case PENDING_DIRECTOR -> BoqDocumentStatus.PENDING_CLIENT;
            case PENDING_CLIENT -> BoqDocumentStatus.APPROVED;
            default -> current;
        };
    }

    public BoqDocumentStatus inboxStatusForRole(Role role) {
        return switch (role) {
            case SENIOR_QS -> BoqDocumentStatus.PENDING_SENIOR_QS;
            case PROJECT_MANAGER -> BoqDocumentStatus.PENDING_PM;
            case BUSINESS_OWNER -> BoqDocumentStatus.PENDING_DIRECTOR;
            case CLIENT -> BoqDocumentStatus.PENDING_CLIENT;
            default -> null;
        };
    }

    /**
     * Inbox is one approval step at a time. When the portal sends a role, only that
     * step is returned (PM never sees PENDING_SENIOR_QS). Without a role param,
     * union all inbox steps the account can act on.
     */
    public List<BoqDocumentStatus> inboxStatusesForPrincipal(AuthPrincipal principal, String roleParam) {
        if (roleParam != null && !roleParam.isBlank()) {
            Role requested = parseRoleParam(roleParam);
            if (requested == null) {
                return List.of();
            }
            if (!hasRole(principal, requested) && !hasRole(principal, Role.SUPER_ADMIN)) {
                return List.of();
            }
            BoqDocumentStatus status = inboxStatusForRole(requested);
            return status == null ? List.of() : List.of(status);
        }
        List<BoqDocumentStatus> statuses = new ArrayList<>();
        if (principal.getRoles() == null) {
            return List.of();
        }
        for (Role role : principal.getRoles()) {
            BoqDocumentStatus status = inboxStatusForRole(role);
            if (status != null) {
                statuses.add(status);
            }
        }
        return statuses;
    }

    public Role parseRoleParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean hasRole(AuthPrincipal principal, Role role) {
        return principal.getRoles() != null && principal.getRoles().contains(role);
    }

    private boolean hasAnyRole(AuthPrincipal principal, Set<Role> roles) {
        return principal.getRoles() != null && principal.getRoles().stream().anyMatch(roles::contains);
    }
}
