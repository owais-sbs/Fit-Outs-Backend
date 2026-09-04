package com.fitouts.validation.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.domain.ActivityProgressUpdate;
import com.fitouts.schedule.domain.ActivityProgressUpdateRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.subcontractor.api.SubcontractorClaimResponse;
import com.fitouts.subcontractor.domain.SubcontractorClaim;
import com.fitouts.subcontractor.domain.SubcontractorClaimRepository;
import com.fitouts.subcontractor.domain.SubcontractorClaimStatus;
import com.fitouts.subcontractor.domain.SubcontractorPackage;
import com.fitouts.subcontractor.domain.SubcontractorPackageRepository;
import com.fitouts.validation.api.ProgressValidationResponse;
import com.fitouts.validation.api.ValidationInboxResponse;
import com.fitouts.validation.domain.ProgressValidation;
import com.fitouts.validation.domain.ProgressValidationRepository;
import com.fitouts.validation.domain.ProgressValidationStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ValidationInboxService {

    private final ProgressValidationRepository validationRepository;
    private final SubcontractorClaimRepository claimRepository;
    private final SubcontractorPackageRepository packageRepository;
    private final ActivityProgressUpdateRepository progressRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ProjectService projectService;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public ValidationInboxResponse inbox() {
        requirePmOrAdmin();
        UUID companyId = requireCompany();
        return buildInbox(companyId, null);
    }

    @Transactional(readOnly = true)
    public ValidationInboxResponse inboxForProject(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        return buildInbox(CompanyContext.get(), project.getId());
    }

    private ValidationInboxResponse buildInbox(UUID companyId, Long projectId) {
        List<ProgressValidation> validations = projectId == null
                ? validationRepository.findByCompanyIdAndStatusOrderByCreatedAtDesc(
                        companyId, ProgressValidationStatus.PENDING)
                : validationRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId)
                        .stream()
                        .filter(v -> v.getStatus() == ProgressValidationStatus.PENDING)
                        .toList();

        List<SubcontractorClaim> claims = projectId == null
                ? claimRepository.findByCompanyIdAndStatusOrderBySubmittedAtDesc(
                        companyId, SubcontractorClaimStatus.SUBMITTED)
                : claimRepository.findByProjectIdAndCompanyIdOrderByCreatedAtDesc(projectId, companyId)
                        .stream()
                        .filter(c -> c.getStatus() == SubcontractorClaimStatus.SUBMITTED)
                        .toList();

        Map<Long, Project> projects = loadProjects(validations, claims);
        Map<UUID, ScheduleActivity> activities = loadActivities(validations, companyId);
        Map<UUID, ActivityProgressUpdate> progressUpdates = loadProgressUpdates(validations);
        Map<UUID, SubcontractorPackage> packages = loadPackages(claims, companyId);
        Map<Long, Account> accounts = loadAccounts(validations, claims);

        List<ProgressValidationResponse> progressItems = validations.stream()
                .map(v -> toProgressResponse(v, projects, activities, progressUpdates, accounts))
                .toList();

        List<SubcontractorClaimResponse> claimItems = claims.stream()
                .map(c -> toClaimResponse(c, projects, packages, accounts))
                .toList();

        return ValidationInboxResponse.builder()
                .progressItems(progressItems)
                .claimItems(claimItems)
                .pendingProgressCount(progressItems.size())
                .pendingClaimCount(claimItems.size())
                .build();
    }

    private Map<Long, Project> loadProjects(List<ProgressValidation> validations, List<SubcontractorClaim> claims) {
        Set<Long> projectIds = validations.stream()
                .map(ProgressValidation::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        projectIds.addAll(claims.stream()
                .map(SubcontractorClaim::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        Map<Long, Project> map = new HashMap<>();
        for (Long id : projectIds) {
            try {
                map.put(id, projectService.getById(id));
            } catch (Exception ignored) {
                // skip missing projects
            }
        }
        return map;
    }

    private Map<UUID, ScheduleActivity> loadActivities(List<ProgressValidation> validations, UUID companyId) {
        Set<UUID> activityUuids = validations.stream()
                .map(ProgressValidation::getActivityUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, ScheduleActivity> map = new HashMap<>();
        for (UUID uuid : activityUuids) {
            activityRepository.findByUuidAndCompanyId(uuid, companyId)
                    .ifPresent(a -> map.put(uuid, a));
        }
        return map;
    }

    private Map<UUID, ActivityProgressUpdate> loadProgressUpdates(List<ProgressValidation> validations) {
        Set<UUID> progressUuids = validations.stream()
                .map(ProgressValidation::getProgressUpdateUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, ActivityProgressUpdate> map = new HashMap<>();
        for (UUID uuid : progressUuids) {
            progressRepository.findById(uuid).ifPresent(p -> map.put(uuid, p));
        }
        return map;
    }

    private Map<UUID, SubcontractorPackage> loadPackages(List<SubcontractorClaim> claims, UUID companyId) {
        Set<UUID> packageUuids = claims.stream()
                .map(SubcontractorClaim::getPackageUuid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SubcontractorPackage> map = new HashMap<>();
        for (UUID uuid : packageUuids) {
            packageRepository.findByUuidAndCompanyId(uuid, companyId)
                    .ifPresent(p -> map.put(uuid, p));
        }
        return map;
    }

    private Map<Long, Account> loadAccounts(List<ProgressValidation> validations, List<SubcontractorClaim> claims) {
        Set<Long> accountIds = progressRepository.findAllById(
                        validations.stream()
                                .map(ProgressValidation::getProgressUpdateUuid)
                                .filter(Objects::nonNull)
                                .toList())
                .stream()
                .map(ActivityProgressUpdate::getReportedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        accountIds.addAll(claims.stream()
                .map(SubcontractorClaim::getSubmittedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        Map<Long, Account> map = new HashMap<>();
        for (Long id : accountIds) {
            accountRepository.findById(id).ifPresent(a -> map.put(id, a));
        }
        return map;
    }

    private ProgressValidationResponse toProgressResponse(
            ProgressValidation validation,
            Map<Long, Project> projects,
            Map<UUID, ScheduleActivity> activities,
            Map<UUID, ActivityProgressUpdate> progressUpdates,
            Map<Long, Account> accounts) {
        Project project = projects.get(validation.getProjectId());
        ScheduleActivity activity = activities.get(validation.getActivityUuid());
        ActivityProgressUpdate progress = progressUpdates.get(validation.getProgressUpdateUuid());
        Account reporter = progress != null ? accounts.get(progress.getReportedBy()) : null;

        return ProgressValidationResponse.builder()
                .uuid(validation.getUuid())
                .progressUpdateUuid(validation.getProgressUpdateUuid())
                .activityUuid(validation.getActivityUuid())
                .projectId(validation.getProjectId())
                .status(validation.getStatus())
                .decidedBy(validation.getDecidedBy())
                .decidedAt(validation.getDecidedAt())
                .reason(validation.getReason())
                .createdAt(validation.getCreatedAt())
                .projectName(project != null ? project.getName() : null)
                .activityName(activity != null ? activity.getName() : null)
                .percentComplete(progress != null ? progress.getPercentComplete() : null)
                .progressNotes(progress != null ? progress.getNotes() : null)
                .reportedByName(reporter != null ? displayName(reporter) : null)
                .reportedAt(progress != null ? progress.getReportedAt() : null)
                .photoPaths(progress != null ? progress.getPhotoPaths() : null)
                .build();
    }

    private SubcontractorClaimResponse toClaimResponse(
            SubcontractorClaim claim,
            Map<Long, Project> projects,
            Map<UUID, SubcontractorPackage> packages,
            Map<Long, Account> accounts) {
        Project project = projects.get(claim.getProjectId());
        SubcontractorPackage pkg = packages.get(claim.getPackageUuid());
        Account submitter = claim.getSubmittedBy() != null ? accounts.get(claim.getSubmittedBy()) : null;

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
                .attachmentPaths(claim.getAttachmentPaths())
                .packageName(pkg != null ? pkg.getName() : null)
                .projectName(project != null ? project.getName() : null)
                .subcontractorName(pkg != null ? pkg.getAppointedCompanyName() : null)
                .submittedByName(submitter != null ? displayName(submitter) : null)
                .build();
    }

    private String displayName(Account account) {
        if (account.getFullName() != null && !account.getFullName().isBlank()) {
            return account.getFullName().trim();
        }
        return account.getEmail();
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
}
