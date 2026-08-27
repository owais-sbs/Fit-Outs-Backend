package com.fitouts.shared.security;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;

@Component
public class PortalAccessHelper {

    public AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }

    /** True when the user has CLIENT and no other roles. */
    public boolean isPureClient(AuthPrincipal principal) {
        Set<Role> roles = principal.getRoles();
        if (roles == null || !roles.contains(Role.CLIENT)) {
            return false;
        }
        return roles.stream().allMatch(r -> r == Role.CLIENT);
    }

    public boolean isPureClient() {
        return isPureClient(requirePrincipal());
    }

    public boolean hasRole(AuthPrincipal principal, Role role) {
        return principal.getRoles() != null && principal.getRoles().contains(role);
    }

    public boolean isSiteEngineer(AuthPrincipal principal) {
        return hasRole(principal, Role.SITE_ENGINEER);
    }

    public boolean isStaff(AuthPrincipal principal) {
        if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
            return false;
        }
        return principal.getRoles().stream().anyMatch(r -> r != Role.CLIENT);
    }

    public void requireStaff() {
        AuthPrincipal principal = requirePrincipal();
        if (isPureClient(principal)) {
            throw new ForbiddenException("Staff access required");
        }
    }
}
