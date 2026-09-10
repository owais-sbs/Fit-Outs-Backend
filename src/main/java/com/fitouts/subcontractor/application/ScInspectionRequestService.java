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
import com.fitouts.subcontractor.api.ScInspectionRequestResponse;
import com.fitouts.subcontractor.api.ScInspectionReviewRequest;
import com.fitouts.subcontractor.api.ScInspectionSubmitRequest;
import com.fitouts.subcontractor.domain.ScInspectionRequest;
import com.fitouts.subcontractor.domain.ScInspectionRequestRepository;
import com.fitouts.subcontractor.domain.ScInspectionStatus;
import com.fitouts.subcontractor.domain.ScPortalUser;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;

import java.util.ArrayList;
import org.springframework.web.multipart.MultipartFile;
import com.fitouts.drawing.application.FileStorageService;

import com.fitouts.checklist.mapper.SiteVisitEstimateMapper;
import com.fitouts.subcontractor.domain.ScPackageAward;
import com.fitouts.subcontractor.domain.ScPackageAwardRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScInspectionRequestService {

    private final ScInspectionRequestRepository inspectionRequestRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final ScPackageAwardRepository awardRepository;
    private final ScPortalAccessService portalAccessService;
    private final ProjectService projectService;
    private final FileStorageService fileStorageService;

    @Transactional
    public ScInspectionRequestResponse submitInspection(UUID packageUuid, ScInspectionSubmitRequest request) {
        return submitInspection(packageUuid, request, null);
    }

    @Transactional
    public ScInspectionRequestResponse submitInspection(
            UUID packageUuid, ScInspectionSubmitRequest request, List<MultipartFile> files) {
        AuthPrincipal principal = requireAuthenticated();
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();

        if (packageUuid == null) {
            throw new BadRequestException("packageUuid is required");
        }
        if (request == null || !StringUtils.hasText(request.getInspectionType())) {
            throw new BadRequestException("inspectionType is required");
        }

        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor package not found"));

        boolean authorized = false;
        ScPackageAward award = awardRepository.findByPackageUuid(packageUuid).orElse(null);
        if (award != null && award.getOrganizationUuid().equals(portalUser.getOrganizationUuid())) {
            authorized = true;
        } else if (pkg.getAppointedAccountId() != null && pkg.getAppointedAccountId().equals(portalUser.getAccountId())) {
            authorized = true;
        }
        if (!authorized) {
            throw new ForbiddenException("You are not authorized to submit inspection requests for this package");
        }

        String attachmentPaths = trimToNull(request.getAttachments());

        if (files != null && !files.isEmpty()) {
            List<String> storedPaths = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                validateUploadedFile(file);
                String storedPath = fileStorageService.store(file, companyId, pkg.getProjectId(), "sc-inspections");
                storedPaths.add(SiteVisitEstimateMapper.toFileUrl(storedPath));
            }
            if (!storedPaths.isEmpty()) {
                String newPaths = String.join(",", storedPaths);
                attachmentPaths = StringUtils.hasText(attachmentPaths)
                        ? attachmentPaths + "," + newPaths
                        : newPaths;
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        ScInspectionRequest row = ScInspectionRequest.builder()
                .projectId(pkg.getProjectId())
                .companyId(companyId)
                .packageUuid(packageUuid)
                .organizationUuid(portalUser.getOrganizationUuid())
                .activityUuid(request.getActivityUuid())
                .inspectionType(request.getInspectionType().trim())
                .noticePeriodHours(request.getNoticePeriodHours())
                .description(trimToNull(request.getDescription()))
                .attachments(attachmentPaths)
                .status(ScInspectionStatus.SUBMITTED)
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(inspectionRequestRepository.save(row));
    }

    @Transactional(readOnly = true)
    public List<ScInspectionRequestResponse> listMyInspections(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        ScPortalUser portalUser = portalAccessService.requireActivePortalUser(principal);
        UUID companyId = requireCompany();

        if (packageUuid == null) {
            throw new BadRequestException("packageUuid is required");
        }

        packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Subcontractor package not found"));

        return inspectionRequestRepository
                .findByPackageUuidAndOrganizationUuidAndCompanyIdOrderByCreatedAtDesc(
                        packageUuid, portalUser.getOrganizationUuid(), companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScInspectionRequestResponse> listProjectInspections(Long projectId) {
        requireAuthenticated();
        UUID companyId = requireCompany();
        requireProject(projectId);

        return inspectionRequestRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScInspectionRequestResponse reviewInspection(Long projectId, UUID inspectionUuid, ScInspectionReviewRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        UUID companyId = requireCompany();
        requireProject(projectId);

        if (inspectionUuid == null) {
            throw new BadRequestException("inspectionUuid is required");
        }
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("status is required");
        }

        ScInspectionRequest row = inspectionRequestRepository
                .findByUuidAndProjectIdAndCompanyId(inspectionUuid, projectId, companyId)
                .orElseThrow(() -> new NotFoundException("Inspection request not found"));

        validateStatusTransition(row.getStatus(), request.getStatus());

        row.setStatus(request.getStatus());
        row.setResultNotes(trimToNull(request.getResultNotes()));
        row.setEvidencePaths(trimToNull(request.getEvidencePaths()));
        row.setInspectedBy(principal.getAccountId());
        row.setInspectedAt(OffsetDateTime.now());

        return toResponse(inspectionRequestRepository.save(row));
    }

    private void validateStatusTransition(ScInspectionStatus current, ScInspectionStatus target) {
        if (current == target) {
            return;
        }
        if (current == ScInspectionStatus.APPROVED || current == ScInspectionStatus.REJECTED) {
            throw new BadRequestException("Cannot change status of an already " + current + " inspection request");
        }
        boolean valid = switch (current) {
            case SUBMITTED -> target == ScInspectionStatus.SCHEDULED
                    || target == ScInspectionStatus.APPROVED
                    || target == ScInspectionStatus.REJECTED
                    || target == ScInspectionStatus.RESUBMISSION_REQUESTED;
            case SCHEDULED -> target == ScInspectionStatus.APPROVED
                    || target == ScInspectionStatus.REJECTED
                    || target == ScInspectionStatus.RESUBMISSION_REQUESTED;
            case RESUBMISSION_REQUESTED -> target == ScInspectionStatus.SUBMITTED
                    || target == ScInspectionStatus.SCHEDULED
                    || target == ScInspectionStatus.APPROVED
                    || target == ScInspectionStatus.REJECTED;
            default -> false;
        };
        if (!valid) {
            throw new BadRequestException("Invalid status transition from " + current + " to " + target);
        }
    }

    private ScInspectionRequestResponse toResponse(ScInspectionRequest row) {
        return ScInspectionRequestResponse.builder()
                .uuid(row.getUuid())
                .projectId(row.getProjectId())
                .packageUuid(row.getPackageUuid())
                .organizationUuid(row.getOrganizationUuid())
                .activityUuid(row.getActivityUuid())
                .inspectionType(row.getInspectionType())
                .requestedAt(row.getRequestedAt())
                .noticePeriodHours(row.getNoticePeriodHours())
                .description(row.getDescription())
                .attachments(row.getAttachments())
                .status(row.getStatus())
                .resultNotes(row.getResultNotes())
                .evidencePaths(row.getEvidencePaths())
                .inspectedBy(row.getInspectedBy())
                .inspectedAt(row.getInspectedAt())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
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

    private void validateUploadedFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BadRequestException("File name is required");
        }
        String lower = originalName.toLowerCase();
        if (lower.endsWith(".exe") || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".cmd")) {
            throw new BadRequestException("Executable files are not allowed: " + originalName);
        }
    }
}
