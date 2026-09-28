package com.fitouts.subcontractor.application;

import java.util.List;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.subcontractor.api.ScPortalContextResponse;
import com.fitouts.subcontractor.domain.ScPortalPermissions;
import com.fitouts.subcontractor.domain.ScPortalRole;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScPortalUserRepository;
import com.fitouts.subcontractor.domain.ScPortalUserStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScPortalAccessService {

    private final ScPortalUserRepository portalUserRepository;

    @Transactional(readOnly = true)
    public ScPortalUser requirePortalUser(AuthPrincipal principal) {
        return portalUserRepository.findByAccountId(principal.getAccountId())
                .orElseThrow(() -> new ForbiddenException("Subcontractor portal user not found"));
    }

    @Transactional(readOnly = true)
    public ScPortalUser requireActivePortalUser(AuthPrincipal principal) {
        ScPortalUser user = requirePortalUser(principal);
        if (user.getStatus() == ScPortalUserStatus.DISABLED) {
            throw new ForbiddenException("Subcontractor portal access is disabled");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public void requireRole(AuthPrincipal principal, ScPortalRole... allowed) {
        ScPortalUser user = requireActivePortalUser(principal);
        for (ScPortalRole role : allowed) {
            if (user.getPortalRole() == role) {
                return;
            }
        }
        throw new ForbiddenException("Insufficient subcontractor portal permissions");
    }

    @Transactional(readOnly = true)
    public void requireOrgAdmin(AuthPrincipal principal) {
        requireRole(principal, ScPortalRole.SC_ADMIN);
    }

    @Transactional(readOnly = true)
    public void requireCommercialAccess(AuthPrincipal principal) {
        ScPortalUser user = requireActivePortalUser(principal);
        if (!hasCommercial(user)) {
            throw new ForbiddenException("Commercial access required");
        }
    }

    @Transactional(readOnly = true)
    public void requireExecutionAccess(AuthPrincipal principal) {
        ScPortalUser user = requireActivePortalUser(principal);
        if (!hasExecution(user)) {
            throw new ForbiddenException("Execution access required");
        }
    }

    @Transactional(readOnly = true)
    public void requireTenderingAccess(AuthPrincipal principal) {
        ScPortalUser user = requireActivePortalUser(principal);
        if (!hasTendering(user)) {
            throw new ForbiddenException("Tendering access required");
        }
    }

    @Transactional(readOnly = true)
    public void requireDocumentsAccess(AuthPrincipal principal) {
        ScPortalUser user = requireActivePortalUser(principal);
        if (!hasDocuments(user)) {
            throw new ForbiddenException("Technical documents access required");
        }
    }

    @Transactional(readOnly = true)
    public ScPortalContextResponse buildPortalContext(AuthPrincipal principal) {
        ScPortalUser user = portalUserRepository.findByAccountId(principal.getAccountId()).orElse(null);
        if (user == null) {
            List<String> all = ScPortalPermissions.defaultsForRole(ScPortalRole.SC_ADMIN);
            return ScPortalContextResponse.builder()
                    .portalRole(ScPortalRole.SC_ADMIN.name())
                    .portalUserStatus(ScPortalUserStatus.ACTIVE.name())
                    .canAccessCommercial(true)
                    .canAccessExecution(true)
                    .canAccessTendering(true)
                    .canAccessDocuments(true)
                    .isOrgAdmin(true)
                    .permissions(all)
                    .build();
        }
        ScPortalRole role = user.getPortalRole();
        List<String> permissions = resolvePermissions(user);
        boolean admin = role == ScPortalRole.SC_ADMIN;
        return ScPortalContextResponse.builder()
                .portalRole(role.name())
                .portalUserStatus(user.getStatus().name())
                .canAccessCommercial(admin || ScPortalPermissions.canAccessCommercial(permissions))
                .canAccessExecution(admin || ScPortalPermissions.canAccessExecution(permissions))
                .canAccessTendering(admin || ScPortalPermissions.canAccessTendering(permissions))
                .canAccessDocuments(admin || ScPortalPermissions.canAccessDocuments(permissions))
                .isOrgAdmin(admin)
                .permissions(permissions)
                .build();
    }

    public List<String> resolvePermissions(ScPortalUser user) {
        if (user == null) {
            return List.of();
        }
        if (user.getPortalRole() == ScPortalRole.SC_ADMIN) {
            return ScPortalPermissions.defaultsForRole(ScPortalRole.SC_ADMIN);
        }
        return ScPortalPermissions.resolve(user.getPortalRole(), user.getPermissionsJson(), null);
    }

    public boolean hasCommercial(ScPortalUser user) {
        if (user.getPortalRole() == ScPortalRole.SC_ADMIN) {
            return true;
        }
        return ScPortalPermissions.canAccessCommercial(resolvePermissions(user));
    }

    public boolean hasExecution(ScPortalUser user) {
        if (user.getPortalRole() == ScPortalRole.SC_ADMIN) {
            return true;
        }
        return ScPortalPermissions.canAccessExecution(resolvePermissions(user));
    }

    public boolean hasTendering(ScPortalUser user) {
        if (user.getPortalRole() == ScPortalRole.SC_ADMIN) {
            return true;
        }
        return ScPortalPermissions.canAccessTendering(resolvePermissions(user));
    }

    public boolean hasDocuments(ScPortalUser user) {
        if (user.getPortalRole() == ScPortalRole.SC_ADMIN) {
            return true;
        }
        return ScPortalPermissions.canAccessDocuments(resolvePermissions(user));
    }

    /** @deprecated Prefer permission-aware checks on ScPortalUser. */
    public boolean canAccessCommercial(ScPortalRole role) {
        return role == ScPortalRole.SC_ADMIN || role == ScPortalRole.SC_QS;
    }

    public boolean canAccessExecution(ScPortalRole role) {
        return role == ScPortalRole.SC_ADMIN || role == ScPortalRole.SC_SUPERVISOR;
    }

    public boolean canAccessTendering(ScPortalRole role) {
        return role == ScPortalRole.SC_ADMIN || role == ScPortalRole.SC_ESTIMATOR;
    }

    public boolean canAccessDocuments(ScPortalRole role) {
        return role == ScPortalRole.SC_ADMIN
                || role == ScPortalRole.SC_DOC_CONTROLLER
                || role == ScPortalRole.SC_SUPERVISOR;
    }
}
