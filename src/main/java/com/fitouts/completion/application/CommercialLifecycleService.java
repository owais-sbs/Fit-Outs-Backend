package com.fitouts.completion.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.completion.api.CloseoutChecklistResponse;
import com.fitouts.completion.api.CommerciallyCloseRequest;
import com.fitouts.completion.api.DlpUpdateRequest;
import com.fitouts.completion.api.FinalAccountResponse;
import com.fitouts.completion.domain.CommercialLifecycleStage;
import com.fitouts.completion.domain.ProjectCloseoutChecklist;
import com.fitouts.completion.domain.ProjectCloseoutChecklistRepository;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;

@Service
public class CommercialLifecycleService {

    private static final Set<Role> COMMERCIAL_AUTHORITY =
            EnumSet.of(Role.BUSINESS_OWNER, Role.FINANCE, Role.ADMIN, Role.SUPER_ADMIN);

    private final ProjectService projectService;
    private final ProjectCloseoutChecklistRepository checklistRepository;
    private final CloseoutChecklistService closeoutChecklistService;
    private final FinalAccountService finalAccountService;

    public CommercialLifecycleService(
            ProjectService projectService,
            ProjectCloseoutChecklistRepository checklistRepository,
            @Lazy CloseoutChecklistService closeoutChecklistService,
            @Lazy FinalAccountService finalAccountService) {
        this.projectService = projectService;
        this.checklistRepository = checklistRepository;
        this.closeoutChecklistService = closeoutChecklistService;
        this.finalAccountService = finalAccountService;
    }

    @Transactional(readOnly = true)
    public CommercialLifecycleStage resolveStage(Long projectId) {
        Project project = projectService.getById(projectId);
        ProjectCloseoutChecklist checklist = findChecklist(project.getId()).orElse(null);
        boolean allSatisfied = closeoutChecklistService.isAllSatisfied(project);
        return resolveStage(checklist, allSatisfied);
    }

    public CommercialLifecycleStage resolveStage(ProjectCloseoutChecklist checklist, boolean allSatisfied) {
        if (checklist != null && checklist.isArchived()) {
            return CommercialLifecycleStage.ARCHIVED;
        }
        if (checklist != null && checklist.isCommerciallyClosed()) {
            if (isArchiveEligible(checklist)) {
                return CommercialLifecycleStage.ARCHIVE_ELIGIBLE;
            }
            return CommercialLifecycleStage.DEFECTS_LIABILITY;
        }
        return allSatisfied
                ? CommercialLifecycleStage.READY_FOR_COMMERCIAL_CLOSE
                : CommercialLifecycleStage.NOT_READY;
    }

    /** Stage for list filters: ARCHIVE_ELIGIBLE collapses into DEFECTS_LIABILITY for persistence semantics. */
    public CommercialLifecycleStage resolvePersistedLikeStage(
            ProjectCloseoutChecklist checklist, boolean allSatisfied) {
        CommercialLifecycleStage stage = resolveStage(checklist, allSatisfied);
        if (stage == CommercialLifecycleStage.ARCHIVE_ELIGIBLE) {
            return CommercialLifecycleStage.DEFECTS_LIABILITY;
        }
        return stage;
    }

    public boolean isArchiveEligible(ProjectCloseoutChecklist checklist) {
        if (checklist == null || !checklist.isCommerciallyClosed() || checklist.isArchived()) {
            return false;
        }
        LocalDate end = checklist.getDlpEndDate();
        return end != null && LocalDate.now().isAfter(end);
    }

    public boolean isCommerciallyClosed(Long projectId) {
        return findChecklist(projectId).map(ProjectCloseoutChecklist::isCommerciallyClosed).orElse(false);
    }

    public boolean isArchived(Long projectId) {
        return findChecklist(projectId).map(ProjectCloseoutChecklist::isArchived).orElse(false);
    }

    /**
     * Blocks commercial mutations once the project is commercially closed (DLP or archived).
     */
    public void assertCommercialMutable(Long projectId) {
        ProjectCloseoutChecklist checklist = findChecklist(projectId).orElse(null);
        if (checklist != null && checklist.isArchived()) {
            throw new ForbiddenException("Project is archived and read-only");
        }
        if (checklist != null && checklist.isCommerciallyClosed()) {
            throw new ForbiddenException(
                    "Project is commercially closed; commercial data cannot be edited during defects liability");
        }
    }

    /** Blocks any write that must freeze after archive (e.g. project status, snags). */
    public void assertNotArchived(Long projectId) {
        if (isArchived(projectId)) {
            throw new ForbiddenException("Project is archived and read-only");
        }
    }

    @Transactional
    public CloseoutChecklistResponse commerciallyClose(Long projectId, CommerciallyCloseRequest request) {
        AuthPrincipal principal = requireCommercialAuthority();
        Project project = projectService.getById(projectId);
        CloseoutChecklistResponse checklistView = closeoutChecklistService.get(projectId);
        if (!checklistView.isAllSatisfied()) {
            throw new BadRequestException("Close-out checklist must be fully satisfied before commercial close");
        }
        // Final Account must be available for review
        FinalAccountResponse finalAccount = finalAccountService.get(projectId);
        if (finalAccount == null) {
            throw new BadRequestException("Final Account is not available for review");
        }

        ProjectCloseoutChecklist checklist = getOrCreate(project);
        if (checklist.isCommerciallyClosed()) {
            throw new BadRequestException("Project is already commercially closed");
        }
        if (checklist.isArchived()) {
            throw new BadRequestException("Project is archived");
        }

        applyDlpDates(checklist, request != null ? request.getDlpStartDate() : null,
                request != null ? request.getDlpEndDate() : null,
                request != null ? request.getDlpDurationMonths() : null,
                true);

        checklist.setCommerciallyClosedAt(OffsetDateTime.now());
        checklist.setCommerciallyClosedBy(principal.getAccountId());
        checklistRepository.save(checklist);
        return closeoutChecklistService.get(projectId);
    }

    @Transactional
    public CloseoutChecklistResponse updateDlp(Long projectId, DlpUpdateRequest request) {
        requireCommercialAuthority();
        Project project = projectService.getById(projectId);
        ProjectCloseoutChecklist checklist = findChecklist(project.getId())
                .orElseThrow(() -> new BadRequestException("Project is not commercially closed"));
        if (!checklist.isCommerciallyClosed()) {
            throw new BadRequestException("Project is not commercially closed");
        }
        if (checklist.isArchived()) {
            throw new ForbiddenException("Project is archived and read-only");
        }
        applyDlpDates(
                checklist,
                request != null ? request.getDlpStartDate() : null,
                request != null ? request.getDlpEndDate() : null,
                request != null ? request.getDlpDurationMonths() : null,
                false);
        checklistRepository.save(checklist);
        return closeoutChecklistService.get(projectId);
    }

    @Transactional
    public CloseoutChecklistResponse archive(Long projectId) {
        AuthPrincipal principal = requireCommercialAuthority();
        Project project = projectService.getById(projectId);
        ProjectCloseoutChecklist checklist = findChecklist(project.getId())
                .orElseThrow(() -> new BadRequestException("Project is not commercially closed"));
        if (!checklist.isCommerciallyClosed()) {
            throw new BadRequestException("Project is not commercially closed");
        }
        if (checklist.isArchived()) {
            throw new BadRequestException("Project is already archived");
        }
        if (!isArchiveEligible(checklist)) {
            throw new BadRequestException("Archive is only available after the DLP end date has passed");
        }
        checklist.setArchivedAt(OffsetDateTime.now());
        checklist.setArchivedBy(principal.getAccountId());
        checklistRepository.save(checklist);
        return closeoutChecklistService.get(projectId);
    }

    public void enrichProject(Project project) {
        if (project == null || project.getId() == null) {
            return;
        }
        ProjectCloseoutChecklist checklist = findChecklist(project.getId()).orElse(null);
        boolean allSatisfied;
        if (checklist != null && (checklist.isArchived() || checklist.isCommerciallyClosed())) {
            allSatisfied = true;
        } else {
            allSatisfied = closeoutChecklistService.isAllSatisfied(project);
        }
        CommercialLifecycleStage stage = resolveStage(checklist, allSatisfied);
        project.setCommercialStage(stage.name());
        project.setArchiveEligible(stage == CommercialLifecycleStage.ARCHIVE_ELIGIBLE);
        if (checklist != null) {
            project.setDlpStartDate(checklist.getDlpStartDate());
            project.setDlpEndDate(checklist.getDlpEndDate());
            project.setCommerciallyClosedAt(checklist.getCommerciallyClosedAt());
            project.setArchivedAt(checklist.getArchivedAt());
        }
    }

    public void enrichProjects(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        UUID companyId = CompanyContext.get();
        List<Long> ids = projects.stream().map(Project::getId).filter(Objects::nonNull).toList();
        Map<Long, ProjectCloseoutChecklist> byProject = Map.of();
        if (companyId != null && !ids.isEmpty()) {
            byProject = checklistRepository
                    .findByCompanyIdAndProjectIdIn(companyId, ids)
                    .stream()
                    .collect(Collectors.toMap(ProjectCloseoutChecklist::getProjectId, Function.identity(), (a, b) -> a));
        }
        for (Project project : projects) {
            try {
                ProjectCloseoutChecklist checklist = byProject.get(project.getId());
                boolean allSatisfied;
                if (checklist != null && (checklist.isArchived() || checklist.isCommerciallyClosed())) {
                    allSatisfied = true;
                } else if (checklist == null
                        || !checklist.isFinalInvoiceConfirmed()
                        || !checklist.isAccountingSynced()) {
                    // Invoice and accounting are required, so the stage is NOT_READY.
                    // Skip the per-project variation/snag scan; it times out the list.
                    allSatisfied = false;
                } else {
                    allSatisfied = closeoutChecklistService.isAllSatisfied(project);
                }
                CommercialLifecycleStage stage = resolveStage(checklist, allSatisfied);
                project.setCommercialStage(stage.name());
                project.setArchiveEligible(stage == CommercialLifecycleStage.ARCHIVE_ELIGIBLE);
                if (checklist != null) {
                    project.setDlpStartDate(checklist.getDlpStartDate());
                    project.setDlpEndDate(checklist.getDlpEndDate());
                    project.setCommerciallyClosedAt(checklist.getCommerciallyClosedAt());
                    project.setArchivedAt(checklist.getArchivedAt());
                }
            } catch (RuntimeException ex) {
                project.setCommercialStage(CommercialLifecycleStage.NOT_READY.name());
                project.setArchiveEligible(false);
            }
        }
    }

    private void applyDlpDates(
            ProjectCloseoutChecklist checklist,
            LocalDate start,
            LocalDate end,
            Integer durationMonths,
            boolean requireComplete) {
        LocalDate resolvedStart = start != null ? start : checklist.getDlpStartDate();
        LocalDate resolvedEnd = end;
        Integer resolvedMonths = durationMonths;

        if (resolvedEnd == null && resolvedStart != null && durationMonths != null && durationMonths > 0) {
            resolvedEnd = resolvedStart.plusMonths(durationMonths);
        }
        if (resolvedEnd == null) {
            resolvedEnd = checklist.getDlpEndDate();
        }
        if (resolvedMonths == null && resolvedStart != null && resolvedEnd != null) {
            long months = ChronoUnit.MONTHS.between(resolvedStart, resolvedEnd);
            if (months > 0 && months <= Integer.MAX_VALUE) {
                resolvedMonths = (int) months;
            }
        }
        if (durationMonths != null) {
            resolvedMonths = durationMonths;
            if (resolvedStart != null && end == null && durationMonths > 0) {
                resolvedEnd = resolvedStart.plusMonths(durationMonths);
            }
        }

        if (requireComplete) {
            if (resolvedStart == null) {
                throw new BadRequestException("dlpStartDate is required");
            }
            if (resolvedEnd == null) {
                throw new BadRequestException("dlpEndDate is required (or provide dlpDurationMonths with start)");
            }
        }
        if (resolvedStart != null && resolvedEnd != null && resolvedEnd.isBefore(resolvedStart)) {
            throw new BadRequestException("dlpEndDate must be on or after dlpStartDate");
        }
        if (resolvedStart != null) {
            checklist.setDlpStartDate(resolvedStart);
        }
        if (resolvedEnd != null) {
            checklist.setDlpEndDate(resolvedEnd);
        }
        if (resolvedMonths != null) {
            if (resolvedMonths < 0) {
                throw new BadRequestException("dlpDurationMonths must be non-negative");
            }
            checklist.setDlpDurationMonths(resolvedMonths);
        }
    }

    private Optional<ProjectCloseoutChecklist> findChecklist(Long projectId) {
        UUID companyId = CompanyContext.get();
        return checklistRepository.findByProjectIdAndCompanyId(projectId, companyId);
    }

    private ProjectCloseoutChecklist getOrCreate(Project project) {
        UUID companyId = CompanyContext.get();
        return checklistRepository.findByProjectIdAndCompanyId(project.getId(), companyId)
                .orElseGet(() -> {
                    ProjectCloseoutChecklist created = new ProjectCloseoutChecklist();
                    created.setProjectId(project.getId());
                    created.setCompanyId(companyId);
                    return checklistRepository.save(created);
                });
    }

    private AuthPrincipal requireCommercialAuthority() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() == null
                || principal.getRoles().stream().noneMatch(COMMERCIAL_AUTHORITY::contains)) {
            throw new ForbiddenException(
                    "Only Business Owner, Finance, Admin, or Super Admin may perform commercial close actions");
        }
        return principal;
    }
}
