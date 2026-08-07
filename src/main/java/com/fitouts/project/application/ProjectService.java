package com.fitouts.project.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

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
    }

    public List<Project> getAll() {
        UUID companyId = CompanyContext.get();
        AuthPrincipal principal = currentPrincipalOrNull();
        if (principal != null && isPureClient(principal)) {
            return projectRepository.findByCompanyIdAndClientIdAndIsDeletedFalse(
                    companyId, principal.getAccountId());
        }
        return projectRepository.findByCompanyIdAndIsDeletedFalse(companyId);
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
        return project;
    }

    public Project update(Long id, Project request) {
        Project project = getById(id);
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
        if (request.isActive() != project.isActive()) {
            project.setActive(request.isActive());
        }
        return projectRepository.save(project);
    }

    public Project delete(Long id) {
        Project project = getById(id);
        project.setDeleted(true);
        project.setActive(false);
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
