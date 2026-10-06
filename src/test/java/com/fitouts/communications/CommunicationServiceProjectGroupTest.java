package com.fitouts.communications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.communications.application.CommunicationEmailNotificationService;
import com.fitouts.communications.application.CommunicationEventPublisher;
import com.fitouts.communications.application.CommunicationService;
import com.fitouts.communications.application.ProjectChatService;
import com.fitouts.communications.domain.ChannelType;
import com.fitouts.communications.domain.CommunicationChannel;
import com.fitouts.communications.domain.CommunicationChannelMember;
import com.fitouts.communications.repository.CommunicationChannelMemberRepository;
import com.fitouts.communications.repository.CommunicationChannelRepository;
import com.fitouts.communications.repository.CommunicationMessageRepository;
import com.fitouts.communications.repository.CommunicationOutboxRepository;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.roomcollab.domain.ProjectRoomRepository;
import com.fitouts.roomcollab.domain.RoomMessageRepository;
import com.fitouts.roomcollab.domain.RoomTaskFileVersionRepository;
import com.fitouts.roomcollab.domain.RoomTaskMessageRepository;
import com.fitouts.roomcollab.domain.RoomTaskRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.security.PortalAccessHelper;

class CommunicationServiceProjectGroupTest {

    private final UUID companyId = UUID.randomUUID();
    private final UUID projectGroupId = UUID.randomUUID();
    private final UUID projectDirectId = UUID.randomUUID();
    private final UUID internalId = UUID.randomUUID();

    private CommunicationChannelRepository channelRepository;
    private CommunicationChannelMemberRepository memberRepository;
    private CommunicationMessageRepository messageRepository;
    private CommunicationOutboxRepository outboxRepository;
    private ProjectChatService projectChatService;
    private PortalAccessHelper portalAccess;
    private CommunicationService service;
    private CommunicationChannel projectGroup;

    @BeforeEach
    void setUp() {
        channelRepository = mock(CommunicationChannelRepository.class);
        memberRepository = mock(CommunicationChannelMemberRepository.class);
        messageRepository = mock(CommunicationMessageRepository.class);
        outboxRepository = mock(CommunicationOutboxRepository.class);
        projectChatService = mock(ProjectChatService.class);
        portalAccess = mock(PortalAccessHelper.class);
        service = new CommunicationService(
                channelRepository,
                memberRepository,
                messageRepository,
                outboxRepository,
                mock(ProjectRoomRepository.class),
                mock(RoomTaskRepository.class),
                mock(RoomMessageRepository.class),
                mock(RoomTaskMessageRepository.class),
                mock(RoomTaskFileVersionRepository.class),
                mock(AccountRepository.class),
                mock(CommunicationEventPublisher.class),
                mock(ProjectRepository.class),
                portalAccess,
                mock(CommunicationEmailNotificationService.class),
                mock(CommercialLifecycleService.class),
                projectChatService);

        projectGroup = channel(projectGroupId, ChannelType.PROJECT_GROUP);
        CompanyContext.set(companyId);
        login();
        when(portalAccess.hasRole(any(), eq(Role.ADMIN))).thenReturn(true);
        when(portalAccess.hasRole(any(), eq(Role.SUPER_ADMIN))).thenReturn(false);
        when(portalAccess.isPureClient(any())).thenReturn(false);
        when(memberRepository.findById(any())).thenReturn(Optional.empty());
        when(memberRepository.findByAccountId(any())).thenReturn(List.of());
        when(messageRepository.findFirstByChannelUuidOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(messageRepository.findByChannelUuidOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(outboxRepository.findById(any())).thenReturn(Optional.empty());
        when(outboxRepository.findTop20BySentByOrderBySentAtDesc(any())).thenReturn(List.of());
        when(channelRepository.findMemberChannels(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
    }

    @Test
    void companyInboxDoesNotAutoJoinProjectGroups() {
        CommunicationChannel internal = channel(internalId, ChannelType.INTERNAL);
        CommunicationChannel direct = channel(projectDirectId, ChannelType.PROJECT_DIRECT);
        when(channelRepository.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(projectGroup, direct, internal));

        service.getInbox("ALL");

        ArgumentCaptor<CommunicationChannelMember> saved = ArgumentCaptor.forClass(CommunicationChannelMember.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CommunicationChannelMember::getChannelUuid)
                .containsExactly(internalId);
    }

    @Test
    void readingAProjectGroupDoesNotCreateMembership() {
        when(channelRepository.findById(projectGroupId)).thenReturn(Optional.of(projectGroup));

        service.getMessages(projectGroupId);
        service.markRead(projectGroupId);

        verify(projectChatService, times(2)).assertCanRead(eq(projectGroup), any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void sendingUsesProjectGroupRules() {
        when(channelRepository.findById(projectGroupId)).thenReturn(Optional.of(projectGroup));
        doThrow(new ForbiddenException("Only group members can send messages"))
                .when(projectChatService).assertCanSend(any(), any());

        assertThatThrownBy(() -> service.sendMessage(projectGroupId, "Hello"))
                .isInstanceOf(ForbiddenException.class);
        verify(messageRepository, never()).save(any());
    }

    private CommunicationChannel channel(UUID uuid, ChannelType type) {
        CommunicationChannel channel = new CommunicationChannel();
        channel.setUuid(uuid);
        channel.setCompanyId(companyId);
        channel.setChannelType(type);
        channel.setName(type.name());
        channel.setProjectId(9L);
        channel.setCreatedBy(1L);
        return channel;
    }

    private void login() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .accountId(1L)
                .companyId(companyId)
                .email("admin@fit.test")
                .fullName("Ada Admin")
                .roles(Set.of(Role.ADMIN))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
