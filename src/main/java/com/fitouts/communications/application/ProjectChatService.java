package com.fitouts.communications.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.communications.domain.ChannelType;
import com.fitouts.communications.domain.CommunicationChannel;
import com.fitouts.communications.domain.CommunicationChannelMember;
import com.fitouts.communications.domain.CommunicationChannelMemberId;
import com.fitouts.communications.dto.CreateProjectDirectRequest;
import com.fitouts.communications.dto.CreateProjectGroupRequest;
import com.fitouts.communications.dto.ProjectChatCandidateResponse;
import com.fitouts.communications.dto.ProjectChatGroupResponse;
import com.fitouts.communications.dto.ProjectChatMemberResponse;
import com.fitouts.communications.dto.UpdateProjectGroupMembersRequest;
import com.fitouts.communications.repository.CommunicationChannelMemberRepository;
import com.fitouts.communications.repository.CommunicationChannelRepository;
import com.fitouts.communications.repository.CommunicationMessageRepository;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.project.domain.ProjectTeamAssignment;
import com.fitouts.project.domain.ProjectTeamAssignmentRepository;
import com.fitouts.project.domain.ProjectTeamRole;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.security.PortalAccessHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectChatService {

    private final CommunicationChannelRepository channelRepository;
    private final CommunicationChannelMemberRepository memberRepository;
    private final CommunicationMessageRepository messageRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamAssignmentRepository assignmentRepository;
    private final PortalAccessHelper portalAccess;
    private final CommercialLifecycleService commercialLifecycleService;

    @Transactional(readOnly = true)
    public List<ProjectChatCandidateResponse> listCandidates(Long projectId) {
        AuthPrincipal principal = requirePrincipal();
        rejectSuperAdmin(principal);
        requireCreator(principal);
        Project project = requireProject(projectId, principal);
        return new ArrayList<>(candidateIndex(project).values()).stream()
                .sorted(Comparator.comparing(ProjectChatCandidateResponse::getRoleLabel, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProjectChatCandidateResponse::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectChatCandidateResponse> listDirectCandidates(Long projectId) {
        AuthPrincipal principal = requirePrincipal();
        rejectSuperAdmin(principal);
        Project project = requireProject(projectId, principal);
        Map<Long, ProjectChatCandidateResponse> candidates = candidateIndex(project);
        boolean callerIsClient = isProjectClient(principal, project, candidates);
        boolean callerMayContactClient = isLead(principal);
        return candidates.values().stream()
                .filter(candidate -> !Objects.equals(candidate.getAccountId(), principal.getAccountId()))
                .filter(candidate -> callerIsClient
                        ? mayBeContactedByClient(candidate)
                        : !candidate.isProjectClient() || callerMayContactClient)
                .sorted(Comparator.comparing(ProjectChatCandidateResponse::getRoleLabel, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProjectChatCandidateResponse::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectChatGroupResponse> listGroups(Long projectId) {
        AuthPrincipal principal = requirePrincipal();
        rejectSuperAdmin(principal);
        Project project = requireProject(projectId, principal);
        Map<Long, ProjectChatCandidateResponse> candidates = candidateIndex(project);
        boolean lead = isLead(principal);
        List<CommunicationChannel> channels = channelRepository
                .findByProjectIdAndCompanyIdAndChannelTypeOrderByCreatedAtAsc(
                        project.getId(), project.getCompanyId(), ChannelType.PROJECT_GROUP);
        List<ProjectChatGroupResponse> groups = new ArrayList<>();
        for (CommunicationChannel channel : channels) {
            boolean member = isMember(channel.getUuid(), principal.getAccountId());
            if (!lead && !member) {
                continue;
            }
            groups.add(toGroup(channel, project, principal, candidates, member));
        }
        List<CommunicationChannel> directs = channelRepository
                .findByProjectIdAndCompanyIdAndChannelTypeOrderByCreatedAtAsc(
                        project.getId(), project.getCompanyId(), ChannelType.PROJECT_DIRECT);
        for (CommunicationChannel channel : directs) {
            if (!isMember(channel.getUuid(), principal.getAccountId())) {
                continue;
            }
            groups.add(toGroup(channel, project, principal, candidates, true));
        }
        return groups;
    }

    @Transactional
    public ProjectChatGroupResponse createGroup(Long projectId, CreateProjectGroupRequest request) {
        AuthPrincipal principal = requirePrincipal();
        rejectSuperAdmin(principal);
        requireCreator(principal);
        Project project = requireProject(projectId, principal);
        commercialLifecycleService.assertNotArchived(project.getId());
        String name = requireName(request != null ? request.getName() : null);
        Map<Long, ProjectChatCandidateResponse> candidates = candidateIndex(project);
        List<Long> memberIds = requireMembers(
                request != null ? request.getMemberAccountIds() : null, candidates);

        CommunicationChannel channel = new CommunicationChannel();
        channel.setCompanyId(project.getCompanyId());
        channel.setChannelType(ChannelType.PROJECT_GROUP);
        channel.setName(name);
        channel.setProjectId(project.getId());
        channel.setCreatedBy(principal.getAccountId());
        channel.setIncludesClient(includesClient(memberIds, candidates));
        channel = channelRepository.save(channel);
        replaceMembers(channel.getUuid(), memberIds);
        return toGroup(channel, project, principal, candidates, isMember(channel.getUuid(), principal.getAccountId()));
    }

    @Transactional
    public ProjectChatGroupResponse startDirect(Long projectId, CreateProjectDirectRequest request) {
        AuthPrincipal principal = requirePrincipal();
        rejectSuperAdmin(principal);
        Project project = requireProject(projectId, principal);
        commercialLifecycleService.assertNotArchived(project.getId());
        Long otherAccountId = request != null ? request.getAccountId() : null;
        if (otherAccountId == null || Objects.equals(otherAccountId, principal.getAccountId())) {
            throw new BadRequestException("Choose someone else to chat with");
        }
        Map<Long, ProjectChatCandidateResponse> candidates = candidateIndex(project);
        ProjectChatCandidateResponse other = candidates.get(otherAccountId);
        if (other == null) {
            throw new BadRequestException("That person is not available for this project");
        }
        if (!mayDirect(principal, project, candidates, other)) {
            throw new ForbiddenException(
                    "Only an Admin, Project Manager, or Project Director can contact the client");
        }

        CommunicationChannel existing = findDirect(project, principal.getAccountId(), otherAccountId);
        if (existing != null) {
            return toGroup(existing, project, principal, candidates, true);
        }

        boolean includesClient = other.isProjectClient() || isProjectClient(principal, project, candidates);
        CommunicationChannel channel = new CommunicationChannel();
        channel.setCompanyId(project.getCompanyId());
        channel.setChannelType(ChannelType.PROJECT_DIRECT);
        channel.setName(other.getDisplayName());
        channel.setProjectId(project.getId());
        channel.setCreatedBy(principal.getAccountId());
        channel.setIncludesClient(includesClient);
        channel = channelRepository.save(channel);
        replaceMembers(channel.getUuid(), List.of(principal.getAccountId(), otherAccountId));
        return toGroup(channel, project, principal, candidates, true);
    }

    @Transactional
    public ProjectChatGroupResponse updateMembers(
            Long projectId, UUID channelUuid, UpdateProjectGroupMembersRequest request) {
        AuthPrincipal principal = requirePrincipal();
        rejectSuperAdmin(principal);
        requireCreator(principal);
        Project project = requireProject(projectId, principal);
        commercialLifecycleService.assertNotArchived(project.getId());
        CommunicationChannel channel = requireGroup(project, channelUuid);
        Map<Long, ProjectChatCandidateResponse> candidates = candidateIndex(project);
        List<Long> memberIds = requireMembers(
                request != null ? request.getMemberAccountIds() : null, candidates);
        channel.setIncludesClient(includesClient(memberIds, candidates));
        channel = channelRepository.save(channel);
        replaceMembers(channel.getUuid(), memberIds);
        return toGroup(channel, project, principal, candidates, isMember(channel.getUuid(), principal.getAccountId()));
    }

    public void assertCanRead(CommunicationChannel channel, AuthPrincipal principal) {
        rejectSuperAdmin(principal);
        if (isDirect(channel)) {
            assertSameCompany(channel, principal);
            if (!isMember(channel.getUuid(), principal.getAccountId())) {
                throw new ForbiddenException("Not allowed to view this chat");
            }
            return;
        }
        requireProjectGroup(channel);
        assertSameCompany(channel, principal);
        if (isLead(principal) || isMember(channel.getUuid(), principal.getAccountId())) {
            return;
        }
        throw new ForbiddenException("Not allowed to view this group");
    }

    public void assertCanSend(CommunicationChannel channel, AuthPrincipal principal) {
        rejectSuperAdmin(principal);
        if (isDirect(channel)) {
            assertSameCompany(channel, principal);
            if (!isMember(channel.getUuid(), principal.getAccountId())) {
                throw new ForbiddenException("Only the two people in this chat can send messages");
            }
            return;
        }
        requireProjectGroup(channel);
        assertSameCompany(channel, principal);
        if (!isMember(channel.getUuid(), principal.getAccountId())) {
            throw new ForbiddenException("Only group members can send messages");
        }
        if (channel.isIncludesClient() && !canSpeakToClient(channel, principal)) {
            throw new ForbiddenException(
                    "Only the Project Manager, Project Director, and the client can message the client");
        }
    }

    private boolean canSpeakToClient(CommunicationChannel channel, AuthPrincipal principal) {
        if (portalAccess.hasRole(principal, Role.PROJECT_MANAGER)
                || portalAccess.hasRole(principal, Role.BUSINESS_OWNER)) {
            return true;
        }
        if (channel.getProjectId() == null) {
            return false;
        }
        return projectRepository.findById(channel.getProjectId())
                .map(project -> Objects.equals(project.getClientId(), principal.getAccountId())
                        || assignmentRepository
                                .findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(
                                        project.getId(), project.getCompanyId())
                                .stream()
                                .anyMatch(a -> Objects.equals(a.getAccountId(), principal.getAccountId())
                                        && a.getRole() == ProjectTeamRole.CLIENT))
                .orElse(false);
    }

    private ProjectChatGroupResponse toGroup(
            CommunicationChannel channel,
            Project project,
            AuthPrincipal principal,
            Map<Long, ProjectChatCandidateResponse> candidates,
            boolean member) {
        List<CommunicationChannelMember> memberships = memberRepository.findByChannelUuid(channel.getUuid());
        List<ProjectChatMemberResponse> members = new ArrayList<>();
        for (CommunicationChannelMember membership : memberships) {
            ProjectChatCandidateResponse candidate = candidates.get(membership.getAccountId());
            String name = candidate != null ? candidate.getDisplayName() : accountName(membership.getAccountId());
            String role = candidate != null ? candidate.getRoleLabel() : "Member";
            members.add(ProjectChatMemberResponse.builder()
                    .accountId(membership.getAccountId())
                    .displayName(name)
                    .roleLabel(role)
                    .build());
        }
        members.sort(Comparator.comparing(ProjectChatMemberResponse::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        var last = messageRepository.findFirstByChannelUuidOrderByCreatedAtDesc(channel.getUuid());
        String lastMessage = last.map(m -> m.getBody() == null ? "" : m.getBody()).orElse("");
        OffsetDateTime lastAt = last.map(m -> m.getCreatedAt()).orElse(channel.getCreatedAt());
        int unread = 0;
        if (member) {
            CommunicationChannelMember mine = memberRepository
                    .findById(new CommunicationChannelMemberId(channel.getUuid(), principal.getAccountId()))
                    .orElse(null);
            OffsetDateTime lastRead = mine != null ? mine.getLastReadAt() : null;
            long count = lastRead == null
                    ? (last.isPresent() ? 1 : 0)
                    : messageRepository.countByChannelUuidAndCreatedAtAfter(channel.getUuid(), lastRead);
            unread = (int) Math.min(count, Integer.MAX_VALUE);
        }
        boolean direct = isDirect(channel);
        String name = channel.getName();
        if (direct) {
            name = members.stream()
                    .filter(item -> !Objects.equals(item.getAccountId(), principal.getAccountId()))
                    .map(ProjectChatMemberResponse::getDisplayName)
                    .findFirst()
                    .orElse(channel.getName());
        }
        boolean canSend = direct
                ? member
                : member && (!channel.isIncludesClient() || canSpeakToClient(channel, principal));
        return ProjectChatGroupResponse.builder()
                .channelUuid(channel.getUuid())
                .name(name)
                .direct(direct)
                .includesClient(channel.isIncludesClient())
                .canSend(canSend)
                .memberCount(members.size())
                .lastMessage(lastMessage)
                .lastMessageAt(lastAt)
                .unreadCount(unread)
                .members(members)
                .build();
    }

    private void replaceMembers(UUID channelUuid, List<Long> memberIds) {
        Set<Long> desired = new HashSet<>(memberIds);
        for (CommunicationChannelMember existing : memberRepository.findByChannelUuid(channelUuid)) {
            if (!desired.contains(existing.getAccountId())) {
                memberRepository.delete(existing);
            }
        }
        for (Long accountId : desired) {
            if (memberRepository.findById(new CommunicationChannelMemberId(channelUuid, accountId)).isEmpty()) {
                CommunicationChannelMember member = new CommunicationChannelMember();
                member.setChannelUuid(channelUuid);
                member.setAccountId(accountId);
                member.setRole("MEMBER");
                memberRepository.save(member);
            }
        }
    }

    private List<Long> requireMembers(List<Long> raw, Map<Long, ProjectChatCandidateResponse> candidates) {
        if (raw == null || raw.isEmpty()) {
            throw new BadRequestException("Select at least one person");
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : raw) {
            if (id == null || !seen.add(id)) {
                continue;
            }
            ProjectChatCandidateResponse candidate = candidates.get(id);
            if (candidate == null) {
                throw new BadRequestException("That person is not available for this project");
            }
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new BadRequestException("Select at least one person");
        }
        if (includesClient(ids, candidates)) {
            for (Long id : ids) {
                if (!candidates.get(id).isCanJoinClientGroup()) {
                    throw new BadRequestException(
                            "Only the Project Manager, Project Director, and the client can be in a client group");
                }
            }
        }
        return ids;
    }

    private boolean includesClient(List<Long> memberIds, Map<Long, ProjectChatCandidateResponse> candidates) {
        return memberIds.stream().anyMatch(id -> {
            ProjectChatCandidateResponse candidate = candidates.get(id);
            return candidate != null && candidate.isProjectClient();
        });
    }

    private Map<Long, ProjectChatCandidateResponse> candidateIndex(Project project) {
        Map<Long, ProjectChatCandidateResponse> byId = new LinkedHashMap<>();
        List<ProjectTeamAssignment> assignments = assignmentRepository
                .findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(project.getId(), project.getCompanyId());
        for (ProjectTeamAssignment assignment : assignments) {
            Account account = accountRepository.findById(assignment.getAccountId()).orElse(null);
            if (!isSelectable(account)) {
                continue;
            }
            byId.put(account.getId(), toCandidate(account, assignment, project));
        }
        if (project.getClientId() != null && !byId.containsKey(project.getClientId())) {
            accountRepository.findById(project.getClientId()).ifPresent(account -> {
                if (isSelectable(account)) {
                    byId.put(account.getId(), toCandidate(account, null, project));
                }
            });
        }
        addCompanyRole(byId, project, Role.BUSINESS_OWNER);
        addCompanyRole(byId, project, Role.ADMIN);
        return byId;
    }

    private void addCompanyRole(Map<Long, ProjectChatCandidateResponse> byId, Project project, Role role) {
        for (Account account : accountRepository.findAllByCompanyUuidAndRole(project.getCompanyId(), role)) {
            if (!isSelectable(account) || byId.containsKey(account.getId())) {
                continue;
            }
            byId.put(account.getId(), toCandidate(account, null, project));
        }
    }

    private ProjectChatCandidateResponse toCandidate(Account account, ProjectTeamAssignment assignment, Project project) {
        boolean projectClient = Objects.equals(account.getId(), project.getClientId())
                || (assignment != null && assignment.getRole() == ProjectTeamRole.CLIENT);
        boolean manager = hasRole(account, Role.PROJECT_MANAGER)
                || (assignment != null && assignment.getRole() == ProjectTeamRole.PROJECT_MANAGER);
        boolean director = hasRole(account, Role.BUSINESS_OWNER);
        String roleLabel;
        if (assignment != null) {
            roleLabel = assignment.getRole().displayLabel();
        } else if (projectClient) {
            roleLabel = Role.CLIENT.displayLabel();
        } else if (director) {
            roleLabel = Role.BUSINESS_OWNER.displayLabel();
        } else if (hasRole(account, Role.ADMIN)) {
            roleLabel = Role.ADMIN.displayLabel();
        } else {
            roleLabel = "Member";
        }
        String displayName = assignment != null && StringUtils.hasText(assignment.getDisplayName())
                ? assignment.getDisplayName()
                : account.getFullName();
        return ProjectChatCandidateResponse.builder()
                .accountId(account.getId())
                .displayName(displayName)
                .email(account.getEmail())
                .roleLabel(roleLabel)
                .projectClient(projectClient)
                .canJoinClientGroup(projectClient || manager || director)
                .build();
    }

    private boolean isSelectable(Account account) {
        if (account == null || !Boolean.TRUE.equals(account.getIsActive())) {
            return false;
        }
        return !hasRole(account, Role.SUPER_ADMIN);
    }

    private boolean hasRole(Account account, Role role) {
        return account.getRoles() != null && account.getRoles().contains(role);
    }

    private String accountName(Long accountId) {
        return accountRepository.findById(accountId).map(Account::getFullName).orElse("Member");
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("Group name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 255) {
            throw new BadRequestException("Group name is too long");
        }
        return trimmed;
    }

    private CommunicationChannel requireGroup(Project project, UUID channelUuid) {
        CommunicationChannel channel = channelRepository.findById(channelUuid)
                .orElseThrow(() -> new NotFoundException("Group not found"));
        if (channel.getChannelType() != ChannelType.PROJECT_GROUP
                || !Objects.equals(channel.getProjectId(), project.getId())
                || !Objects.equals(channel.getCompanyId(), project.getCompanyId())) {
            throw new NotFoundException("Group not found");
        }
        return channel;
    }

    private void requireProjectGroup(CommunicationChannel channel) {
        if (channel == null || channel.getChannelType() != ChannelType.PROJECT_GROUP) {
            throw new BadRequestException("Not a project group");
        }
    }

    private Project requireProject(Long projectId, AuthPrincipal principal) {
        UUID companyId = resolveCompanyId(principal);
        if (companyId == null) {
            throw new BadRequestException("Company context required");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (!companyId.equals(project.getCompanyId())) {
            throw new NotFoundException("Project not found");
        }
        return project;
    }

    private void assertSameCompany(CommunicationChannel channel, AuthPrincipal principal) {
        UUID companyId = resolveCompanyId(principal);
        if (companyId == null || channel.getCompanyId() == null || !companyId.equals(channel.getCompanyId())) {
            throw new ForbiddenException("Not allowed to view this group");
        }
    }

    private boolean isMember(UUID channelUuid, Long accountId) {
        if (channelUuid == null || accountId == null) {
            return false;
        }
        return memberRepository.findById(new CommunicationChannelMemberId(channelUuid, accountId)).isPresent();
    }

    private void rejectSuperAdmin(AuthPrincipal principal) {
        if (portalAccess.hasRole(principal, Role.SUPER_ADMIN)) {
            throw new ForbiddenException("Super Admin cannot access project chat");
        }
    }

    private void requireCreator(AuthPrincipal principal) {
        if (!isLead(principal)) {
            throw new ForbiddenException(
                    "Only an Admin, Project Manager, or Project Director can manage project groups");
        }
    }

    private boolean mayDirect(
            AuthPrincipal principal,
            Project project,
            Map<Long, ProjectChatCandidateResponse> candidates,
            ProjectChatCandidateResponse other) {
        boolean callerIsClient = isProjectClient(principal, project, candidates);
        if (!callerIsClient && !other.isProjectClient()) {
            return true;
        }
        if (callerIsClient && other.isProjectClient()) {
            return false;
        }
        if (other.isProjectClient()) {
            return isLead(principal);
        }
        return mayBeContactedByClient(other);
    }

    private boolean mayBeContactedByClient(ProjectChatCandidateResponse candidate) {
        if (candidate == null || candidate.isProjectClient()) {
            return false;
        }
        Account account = accountRepository.findById(candidate.getAccountId()).orElse(null);
        if (account != null && (hasRole(account, Role.ADMIN)
                || hasRole(account, Role.PROJECT_MANAGER)
                || hasRole(account, Role.BUSINESS_OWNER))) {
            return true;
        }
        return candidate.isCanJoinClientGroup();
    }

    private boolean isProjectClient(
            AuthPrincipal principal, Project project, Map<Long, ProjectChatCandidateResponse> candidates) {
        if (principal == null || principal.getAccountId() == null) {
            return false;
        }
        if (Objects.equals(project.getClientId(), principal.getAccountId())) {
            return true;
        }
        ProjectChatCandidateResponse self = candidates.get(principal.getAccountId());
        return self != null && self.isProjectClient();
    }

    private CommunicationChannel findDirect(Project project, Long firstAccountId, Long secondAccountId) {
        Set<Long> pair = Set.of(firstAccountId, secondAccountId);
        List<CommunicationChannel> directs = channelRepository
                .findByProjectIdAndCompanyIdAndChannelTypeOrderByCreatedAtAsc(
                        project.getId(), project.getCompanyId(), ChannelType.PROJECT_DIRECT);
        for (CommunicationChannel channel : directs) {
            Set<Long> memberIds = new HashSet<>();
            for (CommunicationChannelMember membership : memberRepository.findByChannelUuid(channel.getUuid())) {
                memberIds.add(membership.getAccountId());
            }
            if (memberIds.equals(pair)) {
                return channel;
            }
        }
        return null;
    }

    private boolean isDirect(CommunicationChannel channel) {
        return channel != null && channel.getChannelType() == ChannelType.PROJECT_DIRECT;
    }

    private boolean isLead(AuthPrincipal principal) {
        if (portalAccess.hasRole(principal, Role.SUPER_ADMIN)) {
            return false;
        }
        return portalAccess.hasRole(principal, Role.ADMIN)
                || portalAccess.hasRole(principal, Role.PROJECT_MANAGER)
                || portalAccess.hasRole(principal, Role.BUSINESS_OWNER);
    }

    private UUID resolveCompanyId(AuthPrincipal principal) {
        UUID fromContext = CompanyContext.get();
        if (fromContext != null) {
            return fromContext;
        }
        return principal != null ? principal.getCompanyId() : null;
    }

    private AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BadRequestException("Authentication required");
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        String email = auth.getName();
        if (email != null && email.contains("@")) {
            return accountRepository.findByEmailWithCompany(email.trim().toLowerCase())
                    .map(AuthPrincipal::from)
                    .orElseThrow(() -> new BadRequestException("Authentication required"));
        }
        throw new BadRequestException("Authentication required");
    }
}
