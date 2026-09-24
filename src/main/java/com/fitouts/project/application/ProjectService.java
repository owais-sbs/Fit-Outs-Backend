package com.fitouts.project.application;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.boq.domain.BoqDocumentRepository;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.lead.domain.Lead;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.enums.BoqDocumentStatus;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    private final CommercialLifecycleService commercialLifecycleService;
    private final BoqDocumentRepository boqDocumentRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            @Lazy CommercialLifecycleService commercialLifecycleService,
            BoqDocumentRepository boqDocumentRepository) {
        this.commercialLifecycleService = commercialLifecycleService;
        this.boqDocumentRepository = boqDocumentRepository;
    }

    @Transactional
    public Project create(Project request) {
        request.setId(null);
        request.setActive(true);
        request.setDeleted(false);
        if (request.getCompanyId() == null) {
            request.setCompanyId(CompanyContext.get());
        }
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus("Planning");
        }
        if (request.getProgress() == null) {
            request.setProgress(0);
        }
        return projectRepository.save(request);
        Project saved = projectRepository.save(request);
        try {
            commercialLifecycleService.enrichProject(saved);
        } catch (RuntimeException ex) {
            // Ignore enrichment failures on create.
        }
        return saved;
    }

    public List<Project> getAll() {
        UUID companyId = CompanyContext.get();
        AuthPrincipal principal = currentPrincipalOrNull();
        if (principal != null && isPureClient(principal)) {
            return projectRepository.findByCompanyIdAndClientIdAndIsDeletedFalse(
                    companyId, principal.getAccountId());
        }
        return projectRepository.findByCompanyIdAndIsDeletedFalse(companyId);
        List<Project> projects;
        boolean client = principal != null && isPureClient(principal);
        if (client) {
            projects = projectRepository.findByCompanyIdAndClientIdAndIsDeletedFalse(
            attachApprovedBoqFlags(companyId, projects);
            // Clients do not need Module 27 commercial enrichment (avoids snag/checklist scans).
            return projects;
        projects = projectRepository.findByCompanyIdAndIsDeletedFalse(companyId);
        attachApprovedBoqFlags(companyId, projects);
        try {
            commercialLifecycleService.enrichProjects(projects);
        } catch (RuntimeException ex) {
            // List must still load if enrichment fails (e.g. pending migration).
        return projects;
    }

    private void attachApprovedBoqFlags(UUID companyId, List<Project> projects) {
        if (companyId == null || projects == null || projects.isEmpty()) {
            return;
        Set<Long> approvedProjectIds = new HashSet<>(boqDocumentRepository.findDistinctProjectIdsByCompanyIdAndStatusIn(
                companyId,
                List.of(BoqDocumentStatus.APPROVED, BoqDocumentStatus.FINAL)));
        for (Project project : projects) {
            project.setHasApprovedBoq(approvedProjectIds.contains(project.getId()));
    }

    public Project getById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        AuthPrincipal principal = currentPrincipalOrNull();
        if (principal != null && isPureClient(principal)
                && (project.getClientId() == null
                        || !project.getClientId().equals(principal.getAccountId()))) {
            throw new ForbiddenException("Not your project");
        }
        if (principal == null || !isPureClient(principal)) {
            try {
                commercialLifecycleService.enrichProject(project);
            } catch (RuntimeException ex) {
                // Detail still loads without commercial stage if enrichment fails.
            }
        }
        return project;
    }

    public Project update(Long id, Project request) {
        Project project = getById(id);
        boolean statusChanging = request.getStatus() != null
                && !request.getStatus().equals(project.getStatus());
        if (statusChanging) {
            commercialLifecycleService.assertNotArchived(id);
        }
        // Full project field edits also blocked when archived
        commercialLifecycleService.assertNotArchived(id);

        if (StringUtils.hasText(request.getName())) {
            project.setName(request.getName());
        }
        if (request.getClientId() != null) {
            project.setClientId(request.getClientId());
        }
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        if (request.getProgress() != null) {
            project.setProgress(request.getProgress());
        }
        if (request.getBudget() != null) {
            project.setBudget(request.getBudget());
        }
        if (request.getLocation() != null) {
            project.setLocation(request.getLocation());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getProjectType() != null) {
            project.setProjectType(request.getProjectType());
        }
        if (request.getAssignedManager() != null) {
            project.setAssignedManager(request.getAssignedManager());
        }
        if (request.getStartDate() != null) {
            project.setStartDate(request.getStartDate());
        }
        if (request.getExpectedCompletionDate() != null) {
            project.setExpectedCompletionDate(request.getExpectedCompletionDate());
        }
        if (request.getJurisdictionPackId() != null) {
            project.setJurisdictionPackId(request.getJurisdictionPackId());
        }
        if (request.getApprovalPropertyTypeId() != null) {
            project.setApprovalPropertyTypeId(request.getApprovalPropertyTypeId());
        }
        if (request.getApprovalProjectNatureId() != null) {
            project.setApprovalProjectNatureId(request.getApprovalProjectNatureId());
        }
        if (request.isActive() != project.isActive()) {
            project.setActive(request.isActive());
        }
        return projectRepository.save(project);
    }

    public Project delete(Long id) {
        Project saved = projectRepository.save(project);
        commercialLifecycleService.enrichProject(saved);
        return saved;

        commercialLifecycleService.assertNotArchived(id);
        Project project = getById(id);
        project.setDeleted(true);
        project.setActive(false);
        return projectRepository.save(project);
    }

    public Project ensureStarterProjectForClient(Lead lead, Long accountId) {
        if (lead == null || accountId == null) {
            return null;
        }
        UUID companyId = lead.getCompanyEntity() != null
                ? lead.getCompanyEntity().getUuid()
                : CompanyContext.get();
        if (companyId == null) {
            return null;
        }
        if (lead.getId() != null) {
            Optional<Project> byLead =
                    projectRepository.findByCompanyIdAndLeadIdAndIsDeletedFalse(companyId, lead.getId());
            if (byLead.isPresent()) {
                return byLead.get();
            }
        }

        Project project = new Project();
        String clientLabel = StringUtils.hasText(lead.getClientName()) ? lead.getClientName().trim() : "Client";
        project.setName(clientLabel + " — Fit-Out Project");
        project.setClientId(accountId);
        project.setCompanyId(companyId);
        project.setLeadId(lead.getId());
        project.setLeadReferenceNo(StringUtils.hasText(lead.getReferenceNo()) ? lead.getReferenceNo().trim() : null);
        project.setStatus("Planning");
        project.setProgress(0);
        if (StringUtils.hasText(lead.getProjectType())) {
            project.setProjectType(lead.getProjectType());
        }
        if (StringUtils.hasText(lead.getNotes())) {
            project.setDescription(lead.getNotes());
        }
        return projectRepository.save(project);
    }

    private AuthPrincipal currentPrincipalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private boolean isPureClient(AuthPrincipal principal) {
        Set<Role> roles = principal.getRoles();
        if (roles == null || !roles.contains(Role.CLIENT)) {
            return false;
        }
        return roles.stream().allMatch(r -> r == Role.CLIENT);
    }
}
