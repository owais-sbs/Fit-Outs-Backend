package com.fitouts.project.application;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.project.api.ProjectTeamAssignmentItemRequest;
import com.fitouts.project.api.ProjectTeamAssignmentResponse;
import com.fitouts.project.api.ProjectTeamAssignmentSyncRequest;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectTeamAssignment;
import com.fitouts.project.domain.ProjectTeamAssignmentRepository;
import com.fitouts.project.domain.ProjectTeamRole;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectTeamAssignmentService {

    private static final Set<ProjectTeamRole> ASSIGNABLE_ROLES = EnumSet.allOf(ProjectTeamRole.class);

    private final ProjectTeamAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final CommercialLifecycleService commercialLifecycleService;

    @Transactional(readOnly = true)
    public List<ProjectTeamAssignmentResponse> list(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        return assignmentRepository
                .findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(project.getId(), companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectTeamAssignmentResponse> sync(Long projectId, ProjectTeamAssignmentSyncRequest request) {
        requireStaff();
        commercialLifecycleService.assertNotArchived(projectId);
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        List<ProjectTeamAssignmentItemRequest> raw =
                request.getAssignments() != null ? request.getAssignments() : List.of();
        // CLIENT is always seeded from Project.clientId — never accept from the request.
        List<ProjectTeamAssignmentItemRequest> items = raw.stream()
                .filter(item -> item.getRole() != ProjectTeamRole.CLIENT)
                .toList();

        Set<String> desiredKeys = new HashSet<>();
        for (ProjectTeamAssignmentItemRequest item : items) {
            if (item.getRole() == null || item.getAccountId() == null) {
                throw new BadRequestException("Each assignment requires role and accountId");
            }
            if (!ASSIGNABLE_ROLES.contains(item.getRole())) {
                throw new BadRequestException("Invalid team role: " + item.getRole());
            }
            String key = assignmentKey(item.getRole(), item.getAccountId());
            if (!desiredKeys.add(key)) {
                throw new BadRequestException("Duplicate assignment for role and account");
            }
            validateAccountForRole(item.getRole(), item.getAccountId(), companyId);
        }

        // Load by projectId only — unique constraint ignores company_id.
        List<ProjectTeamAssignment> existing =
                assignmentRepository.findByProjectIdOrderByRoleAscDisplayNameAsc(project.getId());

        Map<String, ProjectTeamAssignment> existingStaffByKey = new LinkedHashMap<>();
        for (ProjectTeamAssignment assignment : existing) {
            if (assignment.getRole() == ProjectTeamRole.CLIENT) {
                continue;
            }
            existingStaffByKey.putIfAbsent(
                    assignmentKey(assignment.getRole(), assignment.getAccountId()), assignment);
        }

        List<ProjectTeamAssignment> toDelete = new ArrayList<>();
        for (Map.Entry<String, ProjectTeamAssignment> entry : existingStaffByKey.entrySet()) {
            if (!desiredKeys.contains(entry.getKey())) {
                toDelete.add(entry.getValue());
            }
        }
        if (!toDelete.isEmpty()) {
            assignmentRepository.deleteAll(toDelete);
            assignmentRepository.flush();
        }

        Set<ProjectTeamAssignment> deleted = new HashSet<>(toDelete);
        for (ProjectTeamAssignmentItemRequest item : items) {
            String key = assignmentKey(item.getRole(), item.getAccountId());
            ProjectTeamAssignment current = existingStaffByKey.get(key);
            Account account = accountRepository.findByIdAndCompanyUuid(item.getAccountId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Account not found"));

            if (current != null && !deleted.contains(current)) {
                current.setCompanyId(companyId);
                current.setDisplayName(account.getFullName());
                current.setEmail(account.getEmail());
                assignmentRepository.save(current);
                continue;
            }

            ProjectTeamAssignment assignment = new ProjectTeamAssignment();
            assignment.setProjectId(project.getId());
            assignment.setCompanyId(companyId);
            assignment.setAccountId(account.getId());
            assignment.setRole(item.getRole());
            assignment.setDisplayName(account.getFullName());
            assignment.setEmail(account.getEmail());
            assignmentRepository.save(assignment);
        }

        seedClientAssignment(project, companyId);
        syncLegacyProjectManager(project, items);

        return assignmentRepository
                .findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(project.getId(), companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Upserts the CLIENT team row from {@link Project#getClientId()}.
     * Called after sync and optionally after project create. When clientId is null,
     * any existing CLIENT rows for the project are removed.
     */
    @Transactional
    public void seedClientAssignment(Project project) {
        if (project == null || project.getId() == null) {
            return;
        }
        UUID companyId = project.getCompanyId() != null ? project.getCompanyId() : CompanyContext.get();
        if (companyId == null) {
            return;
        }
        seedClientAssignment(project, companyId);
    }

    private void seedClientAssignment(Project project, UUID companyId) {
        List<ProjectTeamAssignment> clientRows = assignmentRepository
                .findByProjectIdOrderByRoleAscDisplayNameAsc(project.getId())
                .stream()
                .filter(a -> a.getRole() == ProjectTeamRole.CLIENT)
                .toList();

        if (project.getClientId() == null) {
            if (!clientRows.isEmpty()) {
                assignmentRepository.deleteAll(clientRows);
                assignmentRepository.flush();
            }
            return;
        }

        validateAccountForRole(ProjectTeamRole.CLIENT, project.getClientId(), companyId);
        Account account = accountRepository.findByIdAndCompanyUuid(project.getClientId(), companyId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        List<ProjectTeamAssignment> stale = clientRows.stream()
                .filter(a -> !account.getId().equals(a.getAccountId()))
                .toList();
        if (!stale.isEmpty()) {
            assignmentRepository.deleteAll(stale);
            assignmentRepository.flush();
        }

        ProjectTeamAssignment match = clientRows.stream()
                .filter(a -> account.getId().equals(a.getAccountId()))
                .findFirst()
                .orElse(null);
        if (match != null) {
            match.setCompanyId(companyId);
            match.setDisplayName(account.getFullName());
            match.setEmail(account.getEmail());
            assignmentRepository.save(match);
            return;
        }

        ProjectTeamAssignment assignment = new ProjectTeamAssignment();
        assignment.setProjectId(project.getId());
        assignment.setCompanyId(companyId);
        assignment.setAccountId(account.getId());
        assignment.setRole(ProjectTeamRole.CLIENT);
        assignment.setDisplayName(account.getFullName());
        assignment.setEmail(account.getEmail());
        assignmentRepository.save(assignment);
    }

    private void syncLegacyProjectManager(Project project, List<ProjectTeamAssignmentItemRequest> items) {
        String managerName = items.stream()
                .filter(i -> i.getRole() == ProjectTeamRole.PROJECT_MANAGER)
                .findFirst()
                .flatMap(i -> accountRepository.findById(i.getAccountId()))
                .map(Account::getFullName)
                .orElse(null);
        project.setAssignedManager(managerName);
        projectRepository.save(project);
    }

    private void validateAccountForRole(ProjectTeamRole teamRole, Long accountId, UUID companyId) {
        Account account = accountRepository.findByIdAndCompanyUuid(accountId, companyId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new BadRequestException("Account is inactive: " + account.getFullName());
        }

        Set<Role> roles = account.getRoles() != null ? account.getRoles() : Set.of();
        boolean valid = switch (teamRole) {
            case QS_SENIOR_QS -> roles.contains(Role.QS) || roles.contains(Role.SENIOR_QS);
            case PROJECT_MANAGER -> roles.contains(Role.PROJECT_MANAGER);
            case SITE_ENGINEER -> roles.contains(Role.SITE_ENGINEER);
            case FINANCE -> roles.contains(Role.FINANCE);
            case CLIENT -> roles.contains(Role.CLIENT);
            case SUBCONTRACTOR -> roles.contains(Role.SUBCONTRACTOR);
        };
        if (!valid) {
            throw new BadRequestException(
                    account.getFullName() + " is not eligible for " + teamRole.displayLabel());
        }
    }

    private static String assignmentKey(ProjectTeamRole role, Long accountId) {
        return role.name() + ":" + accountId;
    }

    private ProjectTeamAssignmentResponse toResponse(ProjectTeamAssignment assignment) {
        return ProjectTeamAssignmentResponse.builder()
                .uuid(assignment.getUuid())
                .projectId(assignment.getProjectId())
                .accountId(assignment.getAccountId())
                .role(assignment.getRole())
                .roleLabel(assignment.getRole().displayLabel())
                .displayName(assignment.getDisplayName())
                .email(assignment.getEmail())
                .build();
    }

    private Project requireProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (project.isDeleted()) {
            throw new NotFoundException("Project not found");
        }
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private void requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
    }
}
