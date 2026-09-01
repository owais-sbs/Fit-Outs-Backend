package com.fitouts.subcontractor.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.application.ClientAccountConversionResult;
import com.fitouts.account.application.ClientPortalInviteService;
import com.fitouts.account.application.AccountService;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.domain.BoqDocument;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.boq.domain.BoqLine;
import com.fitouts.boq.domain.BoqLineRepository;
import com.fitouts.planning.application.PlanningService;
import com.fitouts.planning.domain.PlanAreaStatus;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subcontractor.api.AppointSubcontractorRequest;
import com.fitouts.subcontractor.api.ClaimRejectRequest;
import com.fitouts.subcontractor.api.SubcontractorClaimRequest;
import com.fitouts.subcontractor.api.SubcontractorClaimResponse;
import com.fitouts.subcontractor.api.SubcontractorPackageRequest;
import com.fitouts.subcontractor.api.SubcontractorPackageResponse;
import com.fitouts.subcontractor.domain.SubcontractorClaim;
import com.fitouts.subcontractor.domain.SubcontractorClaimRepository;
import com.fitouts.subcontractor.domain.SubcontractorClaimStatus;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;
import com.fitouts.subcontractor.domain.SubcontractorPackageStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubcontractorService {

    private final SubcontractorPackageRepository packageRepository;
    private final SubcontractorClaimRepository claimRepository;
    private final ProjectService projectService;
    private final PlanningService planningService;
    private final BoqDocumentRepository boqDocumentRepository;
    private final BoqLineRepository boqLineRepository;
    private final AccountService accountService;
    private final ClientPortalInviteService clientPortalInviteService;

    @Transactional(readOnly = true)
    public List<SubcontractorPackageResponse> listPackages(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return packageRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), CompanyContext.get())
                .stream()
                .map(this::toPackageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubcontractorPackageResponse getPackage(Long projectId, UUID uuid) {
        requireStaff();
        requireProject(projectId);
        return toPackageResponse(requirePackageForProject(uuid, projectId));
    }

    @Transactional
    public SubcontractorPackageResponse createPackage(Long projectId, SubcontractorPackageRequest request) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BadRequestException("name is required");
        }

        SubcontractorPackage pkg = new SubcontractorPackage();
        pkg.setProjectId(project.getId());
        pkg.setCompanyId(CompanyContext.get());
        pkg.setName(request.getName().trim());
        pkg.setBoqSectionCode(trimToNull(request.getBoqSectionCode()));
        pkg.setStatus(request.getStatus() != null ? request.getStatus() : SubcontractorPackageStatus.OPEN);
        pkg = packageRepository.save(pkg);
        syncPlanning(project.getId(), principal.getAccountId());
        return toPackageResponse(pkg);
    }

    @Transactional
    public SubcontractorPackageResponse updatePackage(Long projectId, UUID uuid, SubcontractorPackageRequest request) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        SubcontractorPackage pkg = requirePackageForProject(uuid, projectId);

        if (request != null) {
            if (StringUtils.hasText(request.getName())) {
                pkg.setName(request.getName().trim());
            }
            if (request.getBoqSectionCode() != null) {
                pkg.setBoqSectionCode(trimToNull(request.getBoqSectionCode()));
            }
            if (request.getStatus() != null) {
                pkg.setStatus(request.getStatus());
            }
        }
        pkg = packageRepository.save(pkg);
        syncPlanning(pkg.getProjectId(), principal.getAccountId());
        return toPackageResponse(pkg);
    }

    @Transactional
    public void deletePackage(Long projectId, UUID uuid) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        SubcontractorPackage pkg = requirePackageForProject(uuid, projectId);
        Long project = pkg.getProjectId();
        packageRepository.delete(pkg);
        syncPlanning(project, principal.getAccountId());
    }

    @Transactional
    public SubcontractorPackageResponse appoint(Long projectId, UUID uuid, AppointSubcontractorRequest request) {
        AuthPrincipal principal = requireStaff();
        requireProject(projectId);
        if (request == null) {
            throw new BadRequestException("Appoint request is required");
        }

        String companyName = trimToNull(request.getCompanyName());
        ClientAccountConversionResult accountResult;
        if (request.getAccountId() != null) {
            accountResult = accountService.ensureSubcontractorAccount(request.getAccountId(), companyName);
        } else if (StringUtils.hasText(request.getEmail())) {
            accountResult = accountService.createOrUpdateSubcontractorAccount(
                    request.getFullName(),
                    request.getEmail(),
                    request.getPhone(),
                    companyName);
        } else {
            throw new BadRequestException("Select an existing subcontractor or provide email to create one");
        }

        if (companyName == null) {
            var account = accountService.getById(accountResult.clientAccountId());
            companyName = trimToNull(account.getCompanyName());
            if (companyName == null) {
                companyName = trimToNull(account.getFullName());
            }
            if (companyName == null) {
                companyName = trimToNull(request.getFullName());
            }
        }

        SubcontractorPackage pkg = requirePackageForProject(uuid, projectId);
        pkg.setAppointedAccountId(accountResult.clientAccountId());
        pkg.setAppointedCompanyName(companyName);
        if (pkg.getStatus() != SubcontractorPackageStatus.COMPLETE) {
            pkg.setStatus(SubcontractorPackageStatus.IN_PROGRESS);
        }
        pkg = packageRepository.save(pkg);

        String inviteName = pkg.getAppointedCompanyName() != null
                ? pkg.getAppointedCompanyName()
                : (StringUtils.hasText(request.getFullName()) ? request.getFullName().trim() : "there");
        clientPortalInviteService.sendSubcontractorPortalInvite(accountResult.clientAccountId(), inviteName);

        syncPlanning(pkg.getProjectId(), principal.getAccountId());
        return toPackageResponse(pkg);
    }

    @Transactional(readOnly = true)
    public List<SubcontractorPackageResponse> myPackages() {
        AuthPrincipal principal = requireAuthenticated();
        UUID companyId = requireCompany();
        return packageRepository
                .findByAppointedAccountIdAndCompanyIdOrderByCreatedAtDesc(principal.getAccountId(), companyId)
                .stream()
                .map(this::toPackageResponse)
                .toList();
    }

    @Transactional
    public SubcontractorClaimResponse createClaim(UUID packageUuid, SubcontractorClaimRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        UUID companyId = requireCompany();
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Package not found"));
        assertCanClaimOnPackage(principal, pkg);

        SubcontractorClaim claim = new SubcontractorClaim();
        claim.setPackageUuid(pkg.getUuid());
        claim.setProjectId(pkg.getProjectId());
        claim.setCompanyId(companyId);
        claim.setClaimedQty(request != null && request.getClaimedQty() != null
                ? request.getClaimedQty() : BigDecimal.ZERO);
        claim.setPlannedQty(request != null && request.getPlannedQty() != null
                ? request.getPlannedQty() : BigDecimal.ZERO);
        claim.setNotes(request != null ? request.getNotes() : null);
        claim.setStatus(SubcontractorClaimStatus.DRAFT);
        return toClaimResponse(claimRepository.save(claim));
    }

    @Transactional(readOnly = true)
    public List<SubcontractorClaimResponse> listClaimsForPackage(UUID packageUuid) {
        AuthPrincipal principal = requireAuthenticated();
        UUID companyId = requireCompany();
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(packageUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Package not found"));
        assertCanViewPackageClaims(principal, pkg);
        return claimRepository.findByPackageUuidAndCompanyIdOrderByCreatedAtDesc(packageUuid, companyId)
                .stream()
                .map(this::toClaimResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubcontractorClaimResponse> listClaimsForProject(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return claimRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), CompanyContext.get())
                .stream()
                .map(this::toClaimResponse)
                .toList();
    }

    @Transactional
    public SubcontractorClaimResponse submitClaim(UUID claimUuid) {
        AuthPrincipal principal = requireAuthenticated();
        SubcontractorClaim claim = claimRepository.findByUuidAndCompanyId(claimUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(claim.getPackageUuid(), claim.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        assertCanClaimOnPackage(principal, pkg);

        if (claim.getStatus() != SubcontractorClaimStatus.DRAFT
                && claim.getStatus() != SubcontractorClaimStatus.REJECTED) {
            throw new BadRequestException("Only DRAFT or REJECTED claims can be submitted");
        }
        claim.setStatus(SubcontractorClaimStatus.SUBMITTED);
        claim.setSubmittedBy(principal.getAccountId());
        claim.setSubmittedAt(OffsetDateTime.now());
        claim.setDecidedBy(null);
        claim.setDecidedAt(null);
        claim.setReason(null);
        return toClaimResponse(claimRepository.save(claim));
    }

    @Transactional
    public SubcontractorClaimResponse approveClaim(Long projectId, UUID claimUuid) {
        AuthPrincipal principal = requirePmOrAdmin();
        requireProject(projectId);
        SubcontractorClaim claim = requireSubmittedClaim(claimUuid, projectId);
        claim.setStatus(SubcontractorClaimStatus.APPROVED);
        claim.setDecidedBy(principal.getAccountId());
        claim.setDecidedAt(OffsetDateTime.now());
        claim.setReason(null);
        return toClaimResponse(claimRepository.save(claim));
    }

    @Transactional
    public SubcontractorClaimResponse rejectClaim(Long projectId, UUID claimUuid, ClaimRejectRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        requireProject(projectId);
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason is required");
        }
        SubcontractorClaim claim = requireSubmittedClaim(claimUuid, projectId);
        claim.setStatus(SubcontractorClaimStatus.REJECTED);
        claim.setDecidedBy(principal.getAccountId());
        claim.setDecidedAt(OffsetDateTime.now());
        claim.setReason(request.getReason().trim());
        return toClaimResponse(claimRepository.save(claim));
    }

    @Transactional
    public List<SubcontractorPackageResponse> generateFromBoq(Long projectId) {
        AuthPrincipal principal = requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        BoqDocument approved = findLatestApprovedBoq(project.getId(), companyId);
        if (approved == null) {
            throw new BadRequestException("No APPROVED BOQ found for this project");
        }

        List<BoqLine> lines = boqLineRepository.findByBoqIdOrderBySortOrderAsc(approved.getId());
        Map<String, String> categories = new LinkedHashMap<>();
        for (BoqLine line : lines) {
            String code = resolveCategoryCode(line);
            categories.putIfAbsent(code, resolveCategoryName(line, code));
        }

        List<SubcontractorPackage> existing = packageRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId);
        Map<String, SubcontractorPackage> bySection = new LinkedHashMap<>();
        for (SubcontractorPackage pkg : existing) {
            if (pkg.getBoqSectionCode() != null) {
                bySection.putIfAbsent(pkg.getBoqSectionCode(), pkg);
            }
        }

        List<SubcontractorPackageResponse> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            String code = entry.getKey();
            SubcontractorPackage pkg = bySection.get(code);
            if (pkg == null) {
                pkg = new SubcontractorPackage();
                pkg.setProjectId(project.getId());
                pkg.setCompanyId(companyId);
                pkg.setName(entry.getValue());
                pkg.setBoqSectionCode(code);
                pkg.setStatus(SubcontractorPackageStatus.OPEN);
                pkg = packageRepository.save(pkg);
            }
            result.add(toPackageResponse(pkg));
        }

        planningService.syncSubcontractorStatus(project.getId(), PlanAreaStatus.IN_PROGRESS, principal.getAccountId());
        return result;
    }

    private BoqDocument findLatestApprovedBoq(Long projectId, UUID companyId) {
        return boqDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(d -> companyId.equals(d.getCompanyId()))
                .filter(d -> d.getStatus() == BoqDocumentStatus.APPROVED || d.getStatus() == BoqDocumentStatus.FINAL)
                .findFirst()
                .orElse(null);
    }

    private static String resolveCategoryCode(BoqLine line) {
        if (StringUtils.hasText(line.getCategoryCode())) {
            return line.getCategoryCode().trim();
        }
        if (StringUtils.hasText(line.getCategoryName())) {
            return line.getCategoryName().trim();
        }
        return "GENERAL";
    }

    private static String resolveCategoryName(BoqLine line, String code) {
        if (StringUtils.hasText(line.getCategoryName())) {
            return line.getCategoryName().trim();
        }
        return code;
    }

    private void syncPlanning(Long projectId, Long updatedBy) {
        UUID companyId = CompanyContext.get();
        long total = packageRepository.countByProjectIdAndCompanyId(projectId, companyId);
        if (total == 0) {
            planningService.syncSubcontractorStatus(projectId, PlanAreaStatus.NOT_STARTED, updatedBy);
            return;
        }
        long complete = packageRepository.countByProjectIdAndCompanyIdAndStatus(
                projectId, companyId, SubcontractorPackageStatus.COMPLETE);
        if (complete == total) {
            planningService.syncSubcontractorStatus(projectId, PlanAreaStatus.READY, updatedBy);
        } else {
            planningService.syncSubcontractorStatus(projectId, PlanAreaStatus.IN_PROGRESS, updatedBy);
        }
    }

    private SubcontractorClaim requireSubmittedClaim(UUID claimUuid, Long projectId) {
        SubcontractorClaim claim = claimRepository.findByUuidAndCompanyId(claimUuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!claim.getProjectId().equals(projectId)) {
            throw new BadRequestException("Claim does not belong to this project");
        }
        if (claim.getStatus() != SubcontractorClaimStatus.SUBMITTED) {
            throw new BadRequestException("Claim is not SUBMITTED");
        }
        return claim;
    }

    private SubcontractorPackage requirePackageForProject(UUID uuid, Long projectId) {
        SubcontractorPackage pkg = packageRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Package not found"));
        if (!pkg.getProjectId().equals(projectId)) {
            throw new BadRequestException("Package does not belong to this project");
        }
        return pkg;
    }

    private void assertCanClaimOnPackage(AuthPrincipal principal, SubcontractorPackage pkg) {
        if (isStaff(principal)) {
            return;
        }
        if (pkg.getAppointedAccountId() != null && pkg.getAppointedAccountId().equals(principal.getAccountId())) {
            return;
        }
        throw new ForbiddenException("Not appointed to this package");
    }

    private void assertCanViewPackageClaims(AuthPrincipal principal, SubcontractorPackage pkg) {
        if (isStaff(principal)) {
            return;
        }
        if (pkg.getAppointedAccountId() != null && pkg.getAppointedAccountId().equals(principal.getAccountId())) {
            return;
        }
        throw new ForbiddenException("Not allowed to view claims for this package");
    }

    private boolean isStaff(AuthPrincipal principal) {
        return principal.getRoles() != null
                && principal.getRoles().stream().anyMatch(r -> r != Role.CLIENT && r != Role.SUBCONTRACTOR);
    }

    private SubcontractorPackageResponse toPackageResponse(SubcontractorPackage pkg) {
        return SubcontractorPackageResponse.builder()
                .uuid(pkg.getUuid())
                .projectId(pkg.getProjectId())
                .companyId(pkg.getCompanyId())
                .name(pkg.getName())
                .boqSectionCode(pkg.getBoqSectionCode())
                .status(pkg.getStatus())
                .appointedAccountId(pkg.getAppointedAccountId())
                .appointedCompanyName(pkg.getAppointedCompanyName())
                .createdAt(pkg.getCreatedAt())
                .updatedAt(pkg.getUpdatedAt())
                .build();
    }

    private SubcontractorClaimResponse toClaimResponse(SubcontractorClaim claim) {
        return SubcontractorClaimResponse.builder()
                .uuid(claim.getUuid())
                .packageUuid(claim.getPackageUuid())
                .projectId(claim.getProjectId())
                .companyId(claim.getCompanyId())
                .claimedQty(claim.getClaimedQty())
                .plannedQty(claim.getPlannedQty())
                .notes(claim.getNotes())
                .status(claim.getStatus())
                .submittedBy(claim.getSubmittedBy())
                .submittedAt(claim.getSubmittedAt())
                .decidedBy(claim.getDecidedBy())
                .decidedAt(claim.getDecidedAt())
                .reason(claim.getReason())
                .createdAt(claim.getCreatedAt())
                .updatedAt(claim.getUpdatedAt())
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

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }

    private AuthPrincipal requirePmOrAdmin() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("PM/Admin access required");
        }
        boolean allowed = principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN)
                || principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.PROJECT_MANAGER);
        if (!allowed) {
            throw new ForbiddenException("PM/Admin access required");
        }
        return principal;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
