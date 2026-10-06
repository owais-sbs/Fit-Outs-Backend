package com.fitouts.communications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.communications.application.ProjectChatService;
import com.fitouts.communications.domain.CommunicationChannel;
import com.fitouts.communications.domain.CommunicationChannelMember;
import com.fitouts.communications.domain.CommunicationChannelMemberId;
import com.fitouts.communications.dto.CreateProjectDirectRequest;
import com.fitouts.communications.dto.CreateProjectGroupRequest;
import com.fitouts.communications.dto.ProjectChatGroupResponse;
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
import com.fitouts.shared.security.PortalAccessHelper;

class ProjectChatServiceTest {

    private final UUID companyId = UUID.randomUUID();
    private final Long projectId = 9L;

    private CommunicationChannelRepository channelRepository;
    private CommunicationChannelMemberRepository memberRepository;
    private CommunicationMessageRepository messageRepository;
    private AccountRepository accountRepository;
    private ProjectRepository projectRepository;
    private ProjectTeamAssignmentRepository assignmentRepository;
    private ProjectChatService service;

    private final List<CommunicationChannel> channels = new ArrayList<>();
    private final List<CommunicationChannelMember> members = new ArrayList<>();
    private final Map<Long, Account> accounts = new HashMap<>();
    private Project project;

    @BeforeEach
    void setUp() {
        channelRepository = mock(CommunicationChannelRepository.class);
        memberRepository = mock(CommunicationChannelMemberRepository.class);
        messageRepository = mock(CommunicationMessageRepository.class);
        accountRepository = mock(AccountRepository.class);
        projectRepository = mock(ProjectRepository.class);
        assignmentRepository = mock(ProjectTeamAssignmentRepository.class);
        service = new ProjectChatService(
                channelRepository,
                memberRepository,
                messageRepository,
                accountRepository,
                projectRepository,
                assignmentRepository,
                new PortalAccessHelper(),
                mock(CommercialLifecycleService.class));

        project = new Project();
        project.setId(projectId);
        project.setCompanyId(companyId);
        project.setClientId(50L);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        accounts.put(1L, account(1L, "Ada Admin", Role.ADMIN));
        accounts.put(2L, account(2L, "Pat Manager", Role.PROJECT_MANAGER));
        accounts.put(3L, account(3L, "Drew Director", Role.BUSINESS_OWNER));
        accounts.put(4L, account(4L, "Sam Site", Role.SITE_ENGINEER));
        accounts.put(5L, account(5L, "Sub Contractor", Role.SUBCONTRACTOR));
        accounts.put(50L, account(50L, "Cara Client", Role.CLIENT));
        accounts.put(99L, account(99L, "Platform", Role.SUPER_ADMIN));
        when(accountRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(accounts.get(inv.getArgument(0))));
        when(accountRepository.findAllByCompanyUuidAndRole(companyId, Role.ADMIN))
                .thenReturn(List.of(accounts.get(1L)));
        when(accountRepository.findAllByCompanyUuidAndRole(companyId, Role.BUSINESS_OWNER))
                .thenReturn(List.of(accounts.get(3L)));

        List<ProjectTeamAssignment> assignments = List.of(
                assignment(2L, ProjectTeamRole.PROJECT_MANAGER, "Pat Manager"),
                assignment(4L, ProjectTeamRole.SITE_ENGINEER, "Sam Site"),
                assignment(5L, ProjectTeamRole.SUBCONTRACTOR, "Sub Contractor"),
                assignment(50L, ProjectTeamRole.CLIENT, "Cara Client"));
        when(assignmentRepository.findByProjectIdAndCompanyIdOrderByRoleAscDisplayNameAsc(projectId, companyId))
                .thenReturn(assignments);

        when(channelRepository.save(any(CommunicationChannel.class))).thenAnswer(inv -> {
            CommunicationChannel channel = inv.getArgument(0);
            if (channel.getUuid() == null) {
                channel.setUuid(UUID.randomUUID());
            }
            channels.removeIf(existing -> channel.getUuid().equals(existing.getUuid()));
            channels.add(channel);
            return channel;
        });
        when(channelRepository.findById(any())).thenAnswer(inv -> channels.stream()
                .filter(channel -> inv.getArgument(0).equals(channel.getUuid()))
                .findFirst());
        when(channelRepository.findByProjectIdAndCompanyIdAndChannelTypeOrderByCreatedAtAsc(any(), any(), any()))
                .thenAnswer(inv -> channels.stream()
                        .filter(channel -> Objects.equals(channel.getProjectId(), inv.getArgument(0))
                                && Objects.equals(channel.getCompanyId(), inv.getArgument(1))
                                && channel.getChannelType() == inv.getArgument(2))
                        .toList());

        when(memberRepository.save(any(CommunicationChannelMember.class))).thenAnswer(inv -> {
            CommunicationChannelMember member = inv.getArgument(0);
            members.removeIf(existing -> sameMember(existing, member));
            members.add(member);
            return member;
        });
        doAnswer(inv -> {
            CommunicationChannelMember member = inv.getArgument(0);
            members.removeIf(existing -> sameMember(existing, member));
            return null;
        }).when(memberRepository).delete(any());
        when(memberRepository.findByChannelUuid(any())).thenAnswer(inv -> members.stream()
                .filter(member -> inv.getArgument(0).equals(member.getChannelUuid()))
                .toList());
        when(memberRepository.findById(any())).thenAnswer(inv -> {
            CommunicationChannelMemberId id = inv.getArgument(0);
            return members.stream()
                    .filter(member -> id.equals(new CommunicationChannelMemberId(
                            member.getChannelUuid(), member.getAccountId())))
                    .findFirst();
        });
        when(messageRepository.findFirstByChannelUuidOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());

        CompanyContext.set(companyId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    void superAdminCannotCreateReadOrSend() {
        login(99L, Role.SUPER_ADMIN);
        assertThatThrownBy(() -> service.createGroup(projectId, request("Site", List.of(4L))))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Super Admin");

        login(1L, Role.ADMIN);
        ProjectChatGroupResponse created = service.createGroup(projectId, request("Site", List.of(4L)));
        CommunicationChannel channel = channels.get(0);

        login(99L, Role.SUPER_ADMIN);
        assertThatThrownBy(() -> service.listGroups(projectId)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.assertCanRead(channel, currentPrincipal()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.assertCanSend(channel, currentPrincipal()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(created.getChannelUuid()).isNotNull();
    }

    @Test
    void onlyAdminProjectManagerAndDirectorCanCreate() {
        login(4L, Role.SITE_ENGINEER);
        assertThatThrownBy(() -> service.createGroup(projectId, request("Site", List.of(4L))))
                .isInstanceOf(ForbiddenException.class);

        login(3L, Role.BUSINESS_OWNER);
        ProjectChatGroupResponse created = service.createGroup(projectId, request("Directors", List.of(4L, 3L)));
        assertThat(created.getMembers()).extracting(member -> member.getAccountId()).containsExactlyInAnyOrder(4L, 3L);
    }

    @Test
    void internalGroupIncludesSelectedStaffAndNotTheCreator() {
        login(1L, Role.ADMIN);
        ProjectChatGroupResponse created = service.createGroup(projectId, request("Site team", List.of(4L, 5L)));

        assertThat(created.isIncludesClient()).isFalse();
        assertThat(created.getMembers()).extracting(member -> member.getAccountId()).containsExactlyInAnyOrder(4L, 5L);
        assertThat(created.isCanSend()).isFalse();
    }

    @Test
    void clientGroupRejectsStaffOtherThanManagerAndDirector() {
        login(1L, Role.ADMIN);
        assertThatThrownBy(() -> service.createGroup(projectId, request("Client", List.of(50L, 4L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("client");
        assertThatThrownBy(() -> service.createGroup(projectId, request("Client", List.of(50L, 5L))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.createGroup(projectId, request("Client", List.of(50L, 1L))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void clientGroupAllowsManagerDirectorAndClientOnly() {
        login(1L, Role.ADMIN);
        ProjectChatGroupResponse created = service.createGroup(
                projectId, request("With client", List.of(2L, 3L, 50L)));

        assertThat(created.isIncludesClient()).isTrue();
        assertThat(created.getMembers()).extracting(member -> member.getAccountId())
                .containsExactlyInAnyOrder(2L, 3L, 50L);
        assertThat(created.isCanSend()).isFalse();

        CommunicationChannel channel = channels.get(0);
        login(2L, Role.PROJECT_MANAGER);
        assertThatCode(() -> service.assertCanSend(channel, currentPrincipal())).doesNotThrowAnyException();
        login(50L, Role.CLIENT);
        assertThatCode(() -> service.assertCanSend(channel, currentPrincipal())).doesNotThrowAnyException();
        login(1L, Role.ADMIN);
        assertThatCode(() -> service.assertCanRead(channel, currentPrincipal())).doesNotThrowAnyException();
        CommunicationChannelMember forced = new CommunicationChannelMember();
        forced.setChannelUuid(channel.getUuid());
        forced.setAccountId(1L);
        members.add(forced);
        assertThatThrownBy(() -> service.assertCanSend(channel, currentPrincipal()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Project Manager");
    }

    @Test
    void leadsSeeEveryGroupAndOtherRolesSeeOnlyTheirOwn() {
        login(1L, Role.ADMIN);
        service.createGroup(projectId, request("Internal", List.of(4L)));
        service.createGroup(projectId, request("Client", List.of(2L, 50L)));

        assertThat(service.listGroups(projectId)).hasSize(2);

        login(4L, Role.SITE_ENGINEER);
        List<ProjectChatGroupResponse> visible = service.listGroups(projectId);
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getName()).isEqualTo("Internal");
        assertThat(visible.get(0).isCanSend()).isTrue();

        login(5L, Role.SUBCONTRACTOR);
        assertThat(service.listGroups(projectId)).isEmpty();
    }

    @Test
    void nonMemberCannotSendAndCannotReadUnlessTheyLeadTheProject() {
        login(1L, Role.ADMIN);
        service.createGroup(projectId, request("Internal", List.of(4L)));
        CommunicationChannel channel = channels.get(0);

        login(2L, Role.PROJECT_MANAGER);
        assertThatCode(() -> service.assertCanRead(channel, currentPrincipal())).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertCanSend(channel, currentPrincipal()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("members");

        login(5L, Role.SUBCONTRACTOR);
        assertThatThrownBy(() -> service.assertCanRead(channel, currentPrincipal()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anyRoleCanStartADirectChatWithInternalStaff() {
        login(4L, Role.SITE_ENGINEER);
        ProjectChatGroupResponse created = service.startDirect(projectId, directRequest(5L));

        assertThat(created.isDirect()).isTrue();
        assertThat(created.isIncludesClient()).isFalse();
        assertThat(created.isCanSend()).isTrue();
        assertThat(created.getName()).isEqualTo("Sub Contractor");
        assertThat(created.getMembers()).extracting(member -> member.getAccountId())
                .containsExactlyInAnyOrder(4L, 5L);

        CommunicationChannel channel = channels.get(0);
        login(5L, Role.SUBCONTRACTOR);
        assertThatCode(() -> service.assertCanSend(channel, currentPrincipal())).doesNotThrowAnyException();
        assertThat(service.listGroups(projectId)).extracting(ProjectChatGroupResponse::getName)
                .containsExactly("Sam Site");

        login(1L, Role.ADMIN);
        assertThat(service.listGroups(projectId)).isEmpty();
        assertThatThrownBy(() -> service.assertCanRead(channel, currentPrincipal()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyAdminProjectManagerAndDirectorCanContactTheClient() {
        login(4L, Role.SITE_ENGINEER);
        assertThat(service.listDirectCandidates(projectId))
                .extracting(candidate -> candidate.getAccountId())
                .contains(1L, 2L, 3L, 5L)
                .doesNotContain(4L, 50L);
        assertThatThrownBy(() -> service.startDirect(projectId, directRequest(50L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("client");

        login(1L, Role.ADMIN);
        ProjectChatGroupResponse created = service.startDirect(projectId, directRequest(50L));
        assertThat(created.isIncludesClient()).isTrue();
        assertThat(created.isCanSend()).isTrue();
        assertThat(created.getName()).isEqualTo("Cara Client");

        CommunicationChannel channel = channels.get(0);
        login(50L, Role.CLIENT);
        assertThatCode(() -> service.assertCanSend(channel, currentPrincipal())).doesNotThrowAnyException();
        assertThat(service.listDirectCandidates(projectId))
                .extracting(candidate -> candidate.getAccountId())
                .contains(1L, 2L, 3L)
                .doesNotContain(4L, 5L, 50L);
        assertThatThrownBy(() -> service.startDirect(projectId, directRequest(4L)))
                .isInstanceOf(ForbiddenException.class);
        ProjectChatGroupResponse withAdmin = service.startDirect(projectId, directRequest(1L));
        assertThat(withAdmin.getChannelUuid()).isEqualTo(created.getChannelUuid());

        login(2L, Role.PROJECT_MANAGER);
        assertThatThrownBy(() -> service.assertCanRead(channel, currentPrincipal()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void startingTheSameDirectChatReusesTheThread() {
        login(4L, Role.SITE_ENGINEER);
        ProjectChatGroupResponse first = service.startDirect(projectId, directRequest(5L));
        ProjectChatGroupResponse second = service.startDirect(projectId, directRequest(5L));

        assertThat(second.getChannelUuid()).isEqualTo(first.getChannelUuid());
        assertThat(channels).hasSize(1);

        login(5L, Role.SUBCONTRACTOR);
        ProjectChatGroupResponse fromOtherSide = service.startDirect(projectId, directRequest(4L));
        assertThat(fromOtherSide.getChannelUuid()).isEqualTo(first.getChannelUuid());
        assertThat(channels).hasSize(1);
    }

    @Test
    void siteEngineerCannotEditMembers() {
        login(1L, Role.ADMIN);
        ProjectChatGroupResponse created = service.createGroup(projectId, request("Internal", List.of(4L)));

        login(4L, Role.SITE_ENGINEER);
        UpdateProjectGroupMembersRequest update = new UpdateProjectGroupMembersRequest();
        update.setMemberAccountIds(List.of(4L, 5L));
        assertThatThrownBy(() -> service.updateMembers(projectId, created.getChannelUuid(), update))
                .isInstanceOf(ForbiddenException.class);
    }

    private void login(Long accountId, Role... roles) {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(accountId)
                .companyId(companyId)
                .email(accountId + "@fit.test")
                .fullName("User " + accountId)
                .roles(new HashSet<>(Set.of(roles)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private AuthPrincipal currentPrincipal() {
        return (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private CreateProjectDirectRequest directRequest(Long accountId) {
        CreateProjectDirectRequest request = new CreateProjectDirectRequest();
        request.setAccountId(accountId);
        return request;
    }

    private CreateProjectGroupRequest request(String name, List<Long> memberIds) {
        CreateProjectGroupRequest request = new CreateProjectGroupRequest();
        request.setName(name);
        request.setMemberAccountIds(memberIds);
        return request;
    }

    private Account account(Long id, String name, Role role) {
        Account account = new Account();
        account.setId(id);
        account.setFullName(name);
        account.setEmail(name.toLowerCase().replace(' ', '.') + "@fit.test");
        account.setIsActive(true);
        account.setRoles(new HashSet<>(Set.of(role)));
        return account;
    }

    private ProjectTeamAssignment assignment(Long accountId, ProjectTeamRole role, String name) {
        ProjectTeamAssignment assignment = new ProjectTeamAssignment();
        assignment.setProjectId(projectId);
        assignment.setCompanyId(companyId);
        assignment.setAccountId(accountId);
        assignment.setRole(role);
        assignment.setDisplayName(name);
        return assignment;
    }

    private boolean sameMember(CommunicationChannelMember left, CommunicationChannelMember right) {
        return Objects.equals(left.getChannelUuid(), right.getChannelUuid())
                && Objects.equals(left.getAccountId(), right.getAccountId());
    }
}
