package com.fitouts.shared.api;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.projectdoc.domain.ProjectDocument;
import com.fitouts.projectdoc.domain.ProjectDocumentRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.security.PortalAccessHelper;
import com.fitouts.subcontractor.domain.ScCompanyProfile;
import com.fitouts.subcontractor.domain.ScCompanyProfileRepository;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScPortalUserRepository;
import com.fitouts.subcontractor.domain.ScTenantMembershipRepository;

import lombok.RequiredArgsConstructor;

/**
 * Download ACL for {@code /api/files/**}. Pure clients may only load published
 * project documents (and their mirrored drawing paths); staff may load any
 * file under their company prefix. Subcontractor compliance / org / worker uploads use
 * {@code sc-compliance/}, {@code sc-org/}, and {@code sc-worker/} prefixes (no company UUID folder).
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final PortalAccessHelper portalAccessHelper;
    private final ProjectDocumentRepository documentRepository;
    private final ScPortalUserRepository portalUserRepository;
    private final ScCompanyProfileRepository companyProfileRepository;
    private final ScTenantMembershipRepository membershipRepository;
    private final com.fitouts.subcontractor.domain.ScWorkerRepository workerRepository;

    public void assertCanDownload(String relativePath) {
        AuthPrincipal principal = portalAccessHelper.requirePrincipal();
        String path = normalize(relativePath);

        if (path.startsWith("sc-signatures/")) {
            assertCanDownloadSignature(path, principal);
            return;
        }
        if (path.startsWith("sc-compliance/")) {
            assertCanDownloadScCompliance(path, principal);
            return;
        }
        if (path.startsWith("sc-org/")) {
            assertCanDownloadScOrg(path, principal);
            return;
        }
        if (path.startsWith("sc-worker/")) {
            assertCanDownloadScWorker(path, principal);
            return;
        }

        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }

        String companyPrefix = companyId + "/";
        if (!path.startsWith(companyPrefix)) {
            throw new ForbiddenException("Access denied");
        }

        if (!portalAccessHelper.isPureClient(principal)) {
            return;
        }

        boolean libraryPath = path.contains("/documents/") || path.contains("/drawings/");
        if (!libraryPath) {
            // Snags, site visits, claim attachments, etc. — company-scoped is enough.
            return;
        }

        ProjectDocument doc = documentRepository
                .findFirstByFilePathAndCompanyIdAndDeletedFalse(path, companyId)
                .orElse(null);
        if (doc == null || !doc.isPublishedToClient()) {
            throw new ForbiddenException("This document is not shared with you");
        }
    }

    /**
     * Paths: {@code sc-compliance/{profileUuid}/filename.pdf}
     * Allowed for: profile admin, any portal user in the same SC org, or tenant staff reviewing vendors.
     */
    private void assertCanDownloadScCompliance(String path, AuthPrincipal principal) {
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new ForbiddenException("Invalid compliance file path");
        }
        UUID profileUuid;
        try {
            profileUuid = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Invalid compliance file path");
        }

        ScCompanyProfile profile = companyProfileRepository.findById(profileUuid)
                .orElseThrow(() -> new ForbiddenException("Compliance document not found"));

        if (principal.getAccountId() != null && principal.getAccountId().equals(profile.getAdminAccountId())) {
            return;
        }

        if (principal.getAccountId() != null) {
            ScPortalUser portalUser = portalUserRepository.findByAccountId(principal.getAccountId()).orElse(null);
            if (portalUser != null
                    && profile.getOrganizationUuid() != null
                    && profile.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
                return;
            }
        }

        UUID companyId = CompanyContext.get();
        if (companyId != null
                && companyId.equals(profile.getCompanyId())
                && isTenantStaff(principal)) {
            return;
        }

        throw new ForbiddenException("Access denied");
    }

    /**
     * Paths: {@code sc-org/{organizationUuid}/...}
     * Allowed for: portal users of that org, or tenant staff with membership to that org.
     */
    private void assertCanDownloadScOrg(String path, AuthPrincipal principal) {
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new ForbiddenException("Invalid organization file path");
        }
        UUID organizationUuid;
        try {
            organizationUuid = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Invalid organization file path");
        }

        if (principal.getAccountId() != null) {
            ScPortalUser portalUser = portalUserRepository.findByAccountId(principal.getAccountId()).orElse(null);
            if (portalUser != null && organizationUuid.equals(portalUser.getOrganizationUuid())) {
                return;
            }
        }

        UUID companyId = CompanyContext.get();
        if (companyId != null
                && isTenantStaff(principal)
                && membershipRepository.findByOrganizationUuidAndCompanyId(organizationUuid, companyId).isPresent()) {
            return;
        }

        throw new ForbiddenException("Access denied");
    }

    /**
     * Paths: {@code sc-worker/{workerUuid}/filename.pdf}
     * Allowed for: portal users in the worker's org, or tenant staff reviewing that org.
     */
    private void assertCanDownloadScWorker(String path, AuthPrincipal principal) {
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new ForbiddenException("Invalid worker file path");
        }
        UUID workerUuid;
        try {
            workerUuid = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Invalid worker file path");
        }

        var worker = workerRepository.findById(workerUuid)
                .orElseThrow(() -> new ForbiddenException("Worker document not found"));

        if (principal.getAccountId() != null) {
            ScPortalUser portalUser = portalUserRepository.findByAccountId(principal.getAccountId()).orElse(null);
            if (portalUser != null
                    && worker.getOrganizationUuid() != null
                    && worker.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
                return;
            }
        }

        UUID companyId = CompanyContext.get();
        if (companyId != null
                && isTenantStaff(principal)
                && worker.getOrganizationUuid() != null
                && membershipRepository.findByOrganizationUuidAndCompanyId(worker.getOrganizationUuid(), companyId).isPresent()) {
            return;
        }

        // Also allow via profile company match for staff
        if (companyId != null && isTenantStaff(principal) && worker.getProfileUuid() != null) {
            ScCompanyProfile profile = companyProfileRepository.findById(worker.getProfileUuid()).orElse(null);
            if (profile != null && companyId.equals(profile.getCompanyId())) {
                return;
            }
        }

        throw new ForbiddenException("Access denied");
    }

    private void assertCanDownloadSignature(String path, AuthPrincipal principal) {
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new ForbiddenException("Invalid signature file path");
        }
        UUID targetPortalUserUuid;
        try {
            targetPortalUserUuid = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Invalid signature file path");
        }

        ScPortalUser targetPortalUser = portalUserRepository.findById(targetPortalUserUuid)
                .orElseThrow(() -> new ForbiddenException("Signature not found"));

        if (principal.getAccountId() != null && principal.getAccountId().equals(targetPortalUser.getAccountId())) {
            return;
        }

        UUID companyId = CompanyContext.get();
        if (companyId != null && !portalAccessHelper.isPureClient(principal)) {
        if (companyId != null && isTenantStaff(principal)) {
            return;
        }

        throw new ForbiddenException("Access denied to signature file");
    }

    /** Main-contractor staff (not a pure client / pure subcontractor-only account). */
    private boolean isTenantStaff(AuthPrincipal principal) {
        if (portalAccessHelper.isPureClient(principal)) {
            return false;
        }
        if (principal.getRoles() == null || principal.getRoles().isEmpty()) {
            return false;
        }
        boolean onlySubcontractor = principal.getRoles().stream().allMatch(r -> r == Role.SUBCONTRACTOR);
        return !onlySubcontractor;
    }

    private static String normalize(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new ForbiddenException("Invalid file path");
        }
        String path = relativePath.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }
}
