package com.fitouts.communications.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.communications.domain.ChannelType;
import com.fitouts.communications.domain.CommunicationChannel;
import com.fitouts.communications.domain.CommunicationChannelMember;
import com.fitouts.communications.domain.CommunicationMessage;
import com.fitouts.communications.domain.CommunicationOutbox;
import com.fitouts.communications.dto.ChannelMessageResponse;
import com.fitouts.communications.dto.CreateChannelRequest;
import com.fitouts.communications.dto.InboxItemResponse;
import com.fitouts.communications.repository.CommunicationChannelMemberRepository;
import com.fitouts.communications.repository.CommunicationChannelRepository;
import com.fitouts.communications.repository.CommunicationMessageRepository;
import com.fitouts.communications.repository.CommunicationOutboxRepository;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.roomcollab.domain.ProjectRoom;
import com.fitouts.roomcollab.domain.ProjectRoomRepository;
import com.fitouts.roomcollab.domain.RoomMessage;
import com.fitouts.roomcollab.domain.RoomMessageRepository;
import com.fitouts.roomcollab.domain.RoomTask;
import com.fitouts.roomcollab.domain.RoomTaskFileVersionRepository;
import com.fitouts.roomcollab.domain.RoomTaskMessage;
import com.fitouts.roomcollab.domain.RoomTaskMessageRepository;
import com.fitouts.roomcollab.domain.RoomTaskRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.security.PortalAccessHelper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommunicationService {

    private final CommunicationChannelRepository channelRepository;
    private final CommunicationChannelMemberRepository memberRepository;
    private final CommunicationMessageRepository messageRepository;
    private final CommunicationOutboxRepository outboxRepository;
    private final ProjectRoomRepository projectRoomRepository;
    private final RoomTaskRepository roomTaskRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomTaskMessageRepository roomTaskMessageRepository;
    private final RoomTaskFileVersionRepository roomTaskFileVersionRepository;
    private final AccountRepository accountRepository;
    private final CommunicationEventPublisher eventPublisher;
    private final ProjectRepository projectRepository;
    private final PortalAccessHelper portalAccess;
    private final CommunicationEmailNotificationService emailNotificationService;

    /** Full backfill — call rarely (admin/manual), never on every inbox read. */
    @Transactional
    public void syncProjectChannels() {
        UUID companyId = CompanyContext.get();
        AuthPrincipal principal = requirePrincipal();
        List<ProjectRoom> rooms = projectRoomRepository.findAll().stream()
                .filter(r -> companyId.equals(r.getCompanyId()))
                .toList();
        for (ProjectRoom room : rooms) {
            ensureChannelForProjectRoom(room, principal.getAccountId());
        }
        List<RoomTask> tasks = roomTaskRepository.findAll().stream()
                .filter(t -> companyId.equals(t.getCompanyId()))
                .toList();
        for (RoomTask task : tasks) {
            ensureChannelForRoomTask(task, principal.getAccountId());
        }
    }

    @Transactional
    public void ensureChannelForProjectRoom(ProjectRoom room) {
        Long creatorId = currentAccountIdOrNull();
        ensureChannelForProjectRoom(room, creatorId);
    }

    @Transactional
    public void ensureChannelForRoomTask(RoomTask task) {
        Long creatorId = currentAccountIdOrNull();
        ensureChannelForRoomTask(task, creatorId);
    }

    private void ensureChannelForProjectRoom(ProjectRoom room, Long creatorAccountId) {
        if (room == null || room.getUuid() == null) return;
        List<CommunicationChannel> existing = channelRepository
                .findByProjectRoomIdOrderByCreatedAtAsc(room.getUuid());
        CommunicationChannel ch = existing.isEmpty() ? null : existing.get(0);
        if (ch == null) {
            ch = new CommunicationChannel();
            ch.setCompanyId(room.getCompanyId());
            ch.setChannelType(ChannelType.PROJECT_ROOM);
            ch.setName(room.getFloorLabel() + " · " + room.getName());
            ch.setProjectId(room.getProjectId());
            ch.setProjectRoomId(room.getUuid());
            ch.setCreatedBy(creatorAccountId);
            ch = channelRepository.save(ch);
            if (creatorAccountId != null) {
                ensureMember(ch.getUuid(), creatorAccountId);
            }
        }
        ensureProjectClientMember(ch, room.getProjectId());
    }

    private void ensureChannelForRoomTask(RoomTask task, Long creatorAccountId) {
        if (task == null || task.getUuid() == null) return;
        List<CommunicationChannel> existing = channelRepository
                .findByRoomTaskIdOrderByCreatedAtAsc(task.getUuid());
        CommunicationChannel ch = existing.isEmpty() ? null : existing.get(0);
        if (ch == null) {
            ch = new CommunicationChannel();
            ch.setCompanyId(task.getCompanyId());
            ch.setChannelType(ChannelType.ROOM_TASK);
            ch.setName(task.getTitle());
            ch.setProjectId(task.getProjectId());
            ch.setProjectRoomId(task.getProjectRoomId());
            ch.setRoomTaskId(task.getUuid());
            ch.setCreatedBy(creatorAccountId);
            ch = channelRepository.save(ch);
            if (creatorAccountId != null) {
                ensureMember(ch.getUuid(), creatorAccountId);
            }
        }
        ensureProjectClientMember(ch, task.getProjectId());
    }

    private void ensureProjectClientMember(CommunicationChannel ch, Long projectId) {
        if (projectId == null) return;
        projectRepository.findById(projectId).ifPresent(project -> {
            if (project.getClientId() != null) {
                ensureMember(ch.getUuid(), project.getClientId());
            }
        });
    }

    @Transactional(readOnly = true)
    public List<InboxItemResponse> getInbox(String filter) {
        AuthPrincipal principal = requirePrincipal();
        UUID companyId = CompanyContext.get();
        List<CommunicationChannel> channels = channelRepository.findMemberChannels(
                principal.getAccountId(), companyId);
        if (filter != null && !filter.isBlank() && !"ALL".equalsIgnoreCase(filter)) {
            ChannelType type = parseFilter(filter);
            channels = channels.stream().filter(c -> c.getChannelType() == type).toList();
        }

        Map<UUID, CommunicationChannelMember> membershipByChannel = memberRepository
                .findByAccountId(principal.getAccountId())
                .stream()
                .collect(Collectors.toMap(
                        CommunicationChannelMember::getChannelUuid,
                        Function.identity(),
                        (a, b) -> a));

        List<InboxItemResponse> items = new ArrayList<>();
        for (CommunicationChannel ch : channels) {
            items.add(toInboxItem(ch, membershipByChannel.get(ch.getUuid())));
        }

        if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter)
                || "EMAIL".equalsIgnoreCase(filter)) {
            List<CommunicationOutbox> outbox;
            if (portalAccess.isPureClient(principal)) {
                outbox = outboxRepository.findTop20ByRecipientEmailIgnoreCaseOrderBySentAtDesc(
                        principal.getEmail());
            } else {
                outbox = outboxRepository.findTop20BySentByOrderBySentAtDesc(principal.getAccountId());
            }
            outbox.forEach(out -> items.add(InboxItemResponse.builder()
                    .channelUuid(out.getUuid())
                    .channelType(ChannelType.EMAIL.name())
                    .name(out.getRecipientEmail())
                    .lastMessage(out.getSubject())
                    .lastMessageAt(out.getSentAt())
                    .unreadCount(0)
                    .contextLabel("Sent email")
                    .build()));
        }

        items.sort(Comparator.comparing(
                InboxItemResponse::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    @Transactional(readOnly = true)
    public List<ChannelMessageResponse> getMessages(UUID channelUuid) {
        CommunicationChannel channel = getChannel(channelUuid);
        AuthPrincipal principal = requirePrincipal();
        assertMember(channel, principal.getAccountId());

        if (channel.getChannelType() == ChannelType.PROJECT_ROOM && channel.getProjectRoomId() != null) {
            List<RoomMessage> msgs = roomMessageRepository
                    .findByProjectRoomIdOrderByCreatedAtAsc(channel.getProjectRoomId());
            Map<Long, String> names = loadSenderNames(
                    msgs.stream().map(RoomMessage::getSenderAccountId).collect(Collectors.toSet()));
            return msgs.stream().map(m -> mapRoomMessage(m, channel.getUuid(), names)).toList();
        }
        if (channel.getChannelType() == ChannelType.ROOM_TASK && channel.getRoomTaskId() != null) {
            List<RoomTaskMessage> msgs = roomTaskMessageRepository
                    .findByTaskIdOrderByCreatedAtAsc(channel.getRoomTaskId());
            Map<Long, String> names = loadSenderNames(
                    msgs.stream().map(RoomTaskMessage::getSenderAccountId).collect(Collectors.toSet()));
            return msgs.stream()
                    .map(m -> mapTaskMessage(m, channel.getUuid(), channel.getProjectId(), names))
                    .toList();
        }

        List<CommunicationMessage> msgs = messageRepository.findByChannelUuidOrderByCreatedAtAsc(channelUuid);
        Map<Long, String> names = loadSenderNames(
                msgs.stream().map(CommunicationMessage::getSenderAccountId).collect(Collectors.toSet()));
        return msgs.stream().map(m -> mapCommMessage(m, channelUuid, names)).toList();
    }

    @Transactional
    public ChannelMessageResponse sendMessage(UUID channelUuid, String body) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException("Message body is required");
        }
        CommunicationChannel channel = getChannel(channelUuid);
        AuthPrincipal principal = requirePrincipal();
        assertMember(channel, principal.getAccountId());

        if (channel.getChannelType() == ChannelType.PROJECT_ROOM
                || channel.getChannelType() == ChannelType.ROOM_TASK) {
            throw new BadRequestException("Use project room/task chat endpoints for this channel type");
        }

        CommunicationMessage msg = new CommunicationMessage();
        msg.setChannelUuid(channelUuid);
        msg.setSenderAccountId(principal.getAccountId());
        msg.setBody(body.trim());
        CommunicationMessage saved = messageRepository.save(msg);
        Map<Long, String> names = loadSenderNames(Set.of(principal.getAccountId()));
        ChannelMessageResponse response = mapCommMessage(saved, channelUuid, names);
        eventPublisher.publishMessageCreated(channelUuid, response);
        eventPublisher.publishInboxUpdated(principal.getAccountId());
        emailNotificationService.notifyChannelMessage(
                channelUuid,
                saved.getUuid(),
                principal.getAccountId(),
                names.getOrDefault(principal.getAccountId(), principal.getEmail()),
                body.trim());
        return response;
    }

    @Transactional
    public CommunicationChannel createChannel(CreateChannelRequest request) {
        AuthPrincipal principal = requirePrincipal();
        if (portalAccess.isPureClient(principal)) {
            throw new ForbiddenException("Clients cannot create channels");
        }
        UUID companyId = CompanyContext.get();
        ChannelType type = parseFilter(request.getChannelType());
        if (type != ChannelType.INTERNAL && type != ChannelType.GROUP && type != ChannelType.CLIENT) {
            throw new BadRequestException("Can only create INTERNAL, GROUP, or CLIENT channels");
        }
        CommunicationChannel ch = new CommunicationChannel();
        ch.setCompanyId(companyId);
        ch.setChannelType(type);
        ch.setName(request.getName() != null ? request.getName().trim() : "Conversation");
        ch.setCreatedBy(principal.getAccountId());
        ch = channelRepository.save(ch);
        ensureMember(ch.getUuid(), principal.getAccountId());
        if (request.getMemberAccountIds() != null) {
            for (Long id : request.getMemberAccountIds()) {
                if (id != null) ensureMember(ch.getUuid(), id);
            }
        }
        return ch;
    }

    @Transactional
    public void markRead(UUID channelUuid) {
        AuthPrincipal principal = requirePrincipal();
        CommunicationChannelMember member = memberRepository
                .findById(new com.fitouts.communications.domain.CommunicationChannelMemberId(
                        channelUuid, principal.getAccountId()))
                .orElseGet(() -> {
                    CommunicationChannelMember m = new CommunicationChannelMember();
                    m.setChannelUuid(channelUuid);
                    m.setAccountId(principal.getAccountId());
                    return m;
                });
        member.setLastReadAt(OffsetDateTime.now());
        memberRepository.save(member);
    }

    @Transactional
    public UUID resolveChannelUuid(UUID projectRoomId, UUID roomTaskId) {
        requirePrincipal();
        if (projectRoomId != null) {
            ProjectRoom room = projectRoomRepository.findById(projectRoomId)
                    .orElseThrow(() -> new NotFoundException("Room not found"));
            ensureChannelForProjectRoom(room);
            return channelRepository.findByProjectRoomIdOrderByCreatedAtAsc(projectRoomId).stream()
                    .findFirst()
                    .map(CommunicationChannel::getUuid)
                    .orElseThrow(() -> new NotFoundException("Channel not found for room"));
        }
        if (roomTaskId != null) {
            RoomTask task = roomTaskRepository.findById(roomTaskId)
                    .orElseThrow(() -> new NotFoundException("Task not found"));
            ensureChannelForRoomTask(task);
            return channelRepository.findByRoomTaskIdOrderByCreatedAtAsc(roomTaskId).stream()
                    .findFirst()
                    .map(CommunicationChannel::getUuid)
                    .orElseThrow(() -> new NotFoundException("Channel not found for task"));
        }
        throw new BadRequestException("projectRoomId or roomTaskId required");
    }

    @Transactional
    public void broadcastRoomMessage(ProjectRoom room, RoomMessage message) {
        if (room == null || message == null) return;
        ensureChannelForProjectRoom(room);
        CommunicationChannel channel = channelRepository.findByProjectRoomIdOrderByCreatedAtAsc(room.getUuid())
                .stream()
                .findFirst()
                .orElse(null);
        if (channel == null) return;
        Map<Long, String> names = loadSenderNames(Set.of(message.getSenderAccountId()));
        ChannelMessageResponse payload = mapRoomMessage(message, channel.getUuid(), names);
        publishToChannelMembers(channel.getUuid(), payload);
    }

    @Transactional
    public void broadcastTaskMessage(RoomTask task, RoomTaskMessage message, Long projectId) {
        if (task == null || message == null) return;
        ensureChannelForRoomTask(task);
        CommunicationChannel channel = channelRepository.findByRoomTaskIdOrderByCreatedAtAsc(task.getUuid())
                .stream()
                .findFirst()
                .orElse(null);
        if (channel == null) return;
        Map<Long, String> names = loadSenderNames(Set.of(message.getSenderAccountId()));
        ChannelMessageResponse payload = mapTaskMessage(
                message, channel.getUuid(), projectId != null ? projectId : channel.getProjectId(), names);
        publishToChannelMembers(channel.getUuid(), payload);
    }

    private void publishToChannelMembers(UUID channelUuid, ChannelMessageResponse payload) {
        eventPublisher.publishMessageCreated(channelUuid, payload);
        memberRepository.findByChannelUuid(channelUuid).forEach(m -> {
            if (m.getAccountId() != null) {
                eventPublisher.publishInboxUpdated(m.getAccountId());
            }
        });
    }

    private InboxItemResponse toInboxItem(CommunicationChannel ch, CommunicationChannelMember membership) {
        String lastMessage = "";
        OffsetDateTime lastAt = ch.getCreatedAt();
        OffsetDateTime lastRead = membership != null ? membership.getLastReadAt() : null;
        long unread = 0;

        if (ch.getChannelType() == ChannelType.PROJECT_ROOM && ch.getProjectRoomId() != null) {
            var last = roomMessageRepository.findFirstByProjectRoomIdOrderByCreatedAtDesc(ch.getProjectRoomId());
            if (last.isPresent()) {
                RoomMessage msg = last.get();
                lastMessage = previewText(msg.getBody(), msg.getAttachmentName());
                lastAt = msg.getCreatedAt();
            }
            unread = lastRead == null
                    ? (last.isPresent() ? 1 : 0)
                    : roomMessageRepository.countByProjectRoomIdAndCreatedAtAfter(ch.getProjectRoomId(), lastRead);
        } else if (ch.getChannelType() == ChannelType.ROOM_TASK && ch.getRoomTaskId() != null) {
            var last = roomTaskMessageRepository.findFirstByTaskIdOrderByCreatedAtDesc(ch.getRoomTaskId());
            if (last.isPresent()) {
                RoomTaskMessage msg = last.get();
                lastMessage = previewText(msg.getBody(), msg.getAttachmentName());
                lastAt = msg.getCreatedAt();
            }
            unread = lastRead == null
                    ? (last.isPresent() ? 1 : 0)
                    : roomTaskMessageRepository.countByTaskIdAndCreatedAtAfter(ch.getRoomTaskId(), lastRead);
        } else {
            var last = messageRepository.findFirstByChannelUuidOrderByCreatedAtDesc(ch.getUuid());
            if (last.isPresent()) {
                lastMessage = last.get().getBody();
                lastAt = last.get().getCreatedAt();
            }
            unread = lastRead == null
                    ? (last.isPresent() ? 1 : 0)
                    : messageRepository.countByChannelUuidAndCreatedAtAfter(ch.getUuid(), lastRead);
        }

        return InboxItemResponse.builder()
                .channelUuid(ch.getUuid())
                .channelType(ch.getChannelType().name())
                .name(ch.getName())
                .lastMessage(lastMessage)
                .lastMessageAt(lastAt)
                .unreadCount((int) Math.min(unread, Integer.MAX_VALUE))
                .projectId(ch.getProjectId())
                .projectRoomId(ch.getProjectRoomId())
                .roomTaskId(ch.getRoomTaskId())
                .contextLabel(contextLabel(ch))
                .build();
    }

    private String contextLabel(CommunicationChannel ch) {
        return switch (ch.getChannelType()) {
            case INTERNAL -> "Internal";
            case CLIENT -> "Client";
            case GROUP -> "Group";
            case PROJECT_ROOM -> "Project room";
            case ROOM_TASK -> "Task";
            case EMAIL -> "Email";
        };
    }

    private Map<Long, String> loadSenderNames(Set<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = accountIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        accountRepository.findAllById(ids).forEach(a -> names.put(a.getId(), a.getFullName()));
        return names;
    }

    private ChannelMessageResponse mapCommMessage(
            CommunicationMessage m, UUID channelUuid, Map<Long, String> names) {
        return ChannelMessageResponse.builder()
                .uuid(m.getUuid())
                .channelUuid(channelUuid)
                .senderAccountId(m.getSenderAccountId())
                .senderName(names.getOrDefault(m.getSenderAccountId(),
                        m.getSenderAccountId() != null ? "#" + m.getSenderAccountId() : ""))
                .body(m.getBody())
                .attachmentUrl(m.getAttachmentPath() != null ? "/api/files/" + m.getAttachmentPath() : null)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private ChannelMessageResponse mapRoomMessage(RoomMessage m, UUID channelUuid, Map<Long, String> names) {
        String attachmentUrl = StringUtils.hasText(m.getAttachmentPath())
                ? "/api/files/" + m.getAttachmentPath()
                : null;
        return ChannelMessageResponse.builder()
                .uuid(m.getUuid())
                .channelUuid(channelUuid)
                .senderAccountId(m.getSenderAccountId())
                .senderName(names.getOrDefault(m.getSenderAccountId(),
                        m.getSenderAccountId() != null ? "#" + m.getSenderAccountId() : ""))
                .body(m.getBody())
                .attachmentName(m.getAttachmentName())
                .attachmentUrl(attachmentUrl)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private ChannelMessageResponse mapTaskMessage(
            RoomTaskMessage m, UUID channelUuid, Long projectId, Map<Long, String> names) {
        ChannelMessageResponse.ChannelMessageResponseBuilder builder = ChannelMessageResponse.builder()
                .uuid(m.getUuid())
                .channelUuid(channelUuid)
                .senderAccountId(m.getSenderAccountId())
                .senderName(names.getOrDefault(m.getSenderAccountId(),
                        m.getSenderAccountId() != null ? "#" + m.getSenderAccountId() : ""))
                .body(m.getBody())
                .attachmentName(m.getAttachmentName())
                .createdAt(m.getCreatedAt());
        if (m.getReferencedVersionId() != null && projectId != null) {
            roomTaskFileVersionRepository.findById(m.getReferencedVersionId()).ifPresent(v -> {
                String downloadUrl = "/api/projects/" + projectId + "/room-tasks/" + v.getTaskId()
                        + "/versions/" + v.getUuid() + "/download";
                builder.attachmentUrl(downloadUrl);
                if (!StringUtils.hasText(m.getAttachmentName())) {
                    builder.attachmentName(v.getOriginalName());
                }
            });
        }
        return builder.build();
    }

    private String previewText(String body, String attachmentName) {
        if (StringUtils.hasText(body)) {
            return body;
        }
        if (StringUtils.hasText(attachmentName)) {
            return "Attachment: " + attachmentName;
        }
        return "";
    }

    private void ensureMember(UUID channelUuid, Long accountId) {
        if (memberRepository.findById(
                new com.fitouts.communications.domain.CommunicationChannelMemberId(channelUuid, accountId))
                .isPresent()) {
            return;
        }
        CommunicationChannelMember member = new CommunicationChannelMember();
        member.setChannelUuid(channelUuid);
        member.setAccountId(accountId);
        memberRepository.save(member);
    }

    private CommunicationChannel getChannel(UUID uuid) {
        CommunicationChannel ch = channelRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Channel not found"));
        if (!ch.getCompanyId().equals(CompanyContext.get())) {
            throw new NotFoundException("Channel not found");
        }
        return ch;
    }

    private void assertMember(CommunicationChannel channel, Long accountId) {
        AuthPrincipal principal = requirePrincipal();
        if (portalAccess.isStaff(principal)
                && (channel.getChannelType() == ChannelType.INTERNAL
                        || channel.getChannelType() == ChannelType.GROUP
                        || channel.getChannelType() == ChannelType.CLIENT)) {
            ensureMember(channel.getUuid(), accountId);
            return;
        }
        memberRepository.findById(
                new com.fitouts.communications.domain.CommunicationChannelMemberId(channel.getUuid(), accountId))
                .orElseThrow(() -> new ForbiddenException("Not a channel member"));
    }

    private Long currentAccountIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.getAccountId();
        }
        return null;
    }

    private AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }

    private ChannelType parseFilter(String filter) {
        try {
            return ChannelType.valueOf(filter.toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid channel filter: " + filter);
        }
    }
}
