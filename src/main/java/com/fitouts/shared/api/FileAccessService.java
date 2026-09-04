package com.fitouts.shared.api;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.projectdoc.domain.ProjectDocument;
import com.fitouts.projectdoc.domain.ProjectDocumentRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.security.PortalAccessHelper;

import lombok.RequiredArgsConstructor;

/**
 * Download ACL for {@code /api/files/**}. Pure clients may only load published
 * project documents (and their mirrored drawing paths); staff may load any
 * file under their company prefix.
 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final PortalAccessHelper portalAccessHelper;
    private final ProjectDocumentRepository documentRepository;

    public void assertCanDownload(String relativePath) {
        AuthPrincipal principal = portalAccessHelper.requirePrincipal();
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }

        String path = normalize(relativePath);
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
