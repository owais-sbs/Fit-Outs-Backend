package com.fitouts.subcontractor.application;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.subcontractor.api.ScPortalContextResponse;
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
        if (!canAccessCommercial(user.getPortalRole())) {
            throw new ForbiddenException("Commercial access required");
        }
    }

    @Transactional(readOnly = true)
    public void requireExecutionAccess(AuthPrincipal principal) {
        ScPortalUser user = requireActivePortalUser(principal);
        if (!canAccessExecution(user.getPortalRole())) {
            throw new ForbiddenException("Execution access required");
        }
    }

    @Transactional(readOnly = true)
    public void requireTenderingAccess(AuthPrincipal principal) {
        ScPortalUser user = requireActivePortalUser(principal);
        if (!canAccessTendering(user.getPortalRole())) {
            throw new ForbiddenException("Tendering access required");
        }
    }

    @Transactional(readOnly = true)
    public void requireDocumentsAccess(AuthPrincipal principal) {
        ScPortalUser user = requireActivePortalUser(principal);
        if (!canAccessDocuments(user.getPortalRole())) {
            throw new ForbiddenException("Technical documents access required");
        }
    }

    @Transactional(readOnly = true)
    public ScPortalContextResponse buildPortalContext(AuthPrincipal principal) {
        ScPortalUser user = portalUserRepository.findByAccountId(principal.getAccountId()).orElse(null);
        if (user == null) {
            return ScPortalContextResponse.builder()
                    .portalRole(ScPortalRole.SC_ADMIN.name())
                    .portalUserStatus(ScPortalUserStatus.ACTIVE.name())
                    .canAccessCommercial(true)
                    .canAccessExecution(true)
                    .canAccessTendering(true)
                    .canAccessDocuments(true)
                    .isOrgAdmin(true)
                    .build();
        }
        ScPortalRole role = user.getPortalRole();
        return ScPortalContextResponse.builder()
                .portalRole(role.name())
                .portalUserStatus(user.getStatus().name())
                .canAccessCommercial(canAccessCommercial(role))
                .canAccessExecution(canAccessExecution(role))
                .canAccessTendering(canAccessTendering(role))
                .canAccessDocuments(canAccessDocuments(role))
                .isOrgAdmin(role == ScPortalRole.SC_ADMIN)
                .build();
    }

    public boolean canAccessCommercial(ScPortalRole role) {
        return role == ScPortalRole.SC_ADMIN || role == ScPortalRole.SC_QS;
    }

    public boolean canAccessExecution(ScPortalRole role) {
        // Site execution only — not QS (commercial) or Doc Controller (technical docs only).
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
