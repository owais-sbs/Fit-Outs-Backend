package com.fitouts.subcontractor.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.ScSubmittalRequest;
import com.fitouts.subcontractor.api.ScSubmittalResponse;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.ScSubmittal;
import com.fitouts.subcontractor.domain.ScSubmittalRepository;
import com.fitouts.subcontractor.domain.ScSubmittalStatus;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScSubmittalService {

    private final ScSubmittalRepository submittalRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final ScPortalAccessService portalAccessService;
    private final ProjectService projectService;

    @Transactional(readOnly = true)
    public List<ScSubmittalResponse> listMySubmittals() {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireDocumentsAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        return submittalRepository
                .findByOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
                        portalUser.getOrganizationUuid(), requireCompany())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScSubmittalResponse createSubmittal(ScSubmittalRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        portalAccessService.requireDocumentsAccess(principal);
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        if (request == null || !StringUtils.hasText(request.getTitle()) || request.getProjectId() == null) {
            throw new BadRequestException("title and projectId are required");
        }
        requireProject(request.getProjectId());
        if (request.getPackageUuid() != null) {
            SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(request.getPackageUuid(), requireCompany())
                    .orElseThrow(() -> new NotFoundException("Package not found"));
            if (!pkg.getProjectId().equals(request.getProjectId())) {
                throw new BadRequestException("Package does not belong to this project");
            }
        }
        ScSubmittal row = new ScSubmittal();
        row.setPackageUuid(request.getPackageUuid());
        row.setProjectId(request.getProjectId());
        row.setCompanyId(requireCompany());
        row.setOrganizationUuid(portalUser.getOrganizationUuid());
        row.setTitle(request.getTitle().trim());
        row.setSubmittalType(trimToNull(request.getSubmittalType()));
        row.setReviewCode(trimToNull(request.getReviewCode()));
        row.setFilePaths(trimToNull(request.getFilePaths()));
        row.setStatus(ScSubmittalStatus.SUBMITTED);
        row.setSubmittedBy(principal.getAccountId());
        row.setSubmittedAt(OffsetDateTime.now());
        return toResponse(submittalRepository.save(row));
    }

    private ScSubmittalResponse toResponse(ScSubmittal row) {
        return ScSubmittalResponse.builder()
                .uuid(row.getUuid())
                .packageUuid(row.getPackageUuid())
                .projectId(row.getProjectId())
                .organizationUuid(row.getOrganizationUuid())
                .title(row.getTitle())
                .submittalType(row.getSubmittalType())
                .revisionNo(row.getRevisionNo())
                .reviewCode(row.getReviewCode())
                .status(row.getStatus().name())
                .filePaths(row.getFilePaths())
                .submittedAt(row.getSubmittedAt())
                .reviewedAt(row.getReviewedAt())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Authentication required");
        }
        return principal;
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyId;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
