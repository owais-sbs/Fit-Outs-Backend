package com.fitouts.project.application;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
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
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        List<ProjectTeamAssignmentItemRequest> items =
                request.getAssignments() != null ? request.getAssignments() : List.of();

        Set<String> seen = new HashSet<>();
        for (ProjectTeamAssignmentItemRequest item : items) {
            if (item.getRole() == null || item.getAccountId() == null) {
                throw new BadRequestException("Each assignment requires role and accountId");
            }
            if (!ASSIGNABLE_ROLES.contains(item.getRole())) {
                throw new BadRequestException("Invalid team role: " + item.getRole());
            }
            String key = item.getRole().name() + ":" + item.getAccountId();
            if (!seen.add(key)) {
                throw new BadRequestException("Duplicate assignment for role and account");
            }
            validateAccountForRole(item.getRole(), item.getAccountId(), companyId);
        }

        assignmentRepository.deleteByProjectIdAndCompanyId(project.getId(), companyId);

        for (ProjectTeamAssignmentItemRequest item : items) {
            Account account = accountRepository.findByIdAndCompanyUuid(item.getAccountId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Account not found"));

            ProjectTeamAssignment assignment = new ProjectTeamAssignment();
            assignment.setProjectId(project.getId());
            assignment.setCompanyId(companyId);
            assignment.setAccountId(account.getId());
            assignment.setRole(item.getRole());
            assignment.setDisplayName(account.getFullName());
            assignment.setEmail(account.getEmail());
            assignmentRepository.save(assignment);
        }

        syncLegacyProjectManager(project, items);

        return assignmentRepository
                .findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(project.getId(), companyId)
                .stream()
                .map(this::toResponse)
                .toList();
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
            case FINANCE -> roles.contains(Role.FINANCE);
            case CLIENT -> roles.contains(Role.CLIENT);
            case SUBCONTRACTOR -> roles.contains(Role.SUBCONTRACTOR);
        };
        if (!valid) {
            throw new BadRequestException(
                    account.getFullName() + " is not eligible for " + teamRole.displayLabel());
        }
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
