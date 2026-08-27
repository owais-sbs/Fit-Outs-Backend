package com.fitouts.communications.application;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.communications.domain.CommunicationChannel;
import com.fitouts.communications.domain.CommunicationChannelMember;
import com.fitouts.communications.domain.CommunicationOutbox;
import com.fitouts.communications.repository.CommunicationChannelMemberRepository;
import com.fitouts.communications.repository.CommunicationChannelRepository;
import com.fitouts.communications.repository.CommunicationOutboxRepository;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.roomcollab.domain.ProjectRoom;
import com.fitouts.roomcollab.domain.ProjectRoomRepository;
import com.fitouts.roomcollab.domain.RoomTask;
import com.fitouts.roomcollab.domain.RoomTaskRepository;
import com.fitouts.shared.email.EmailMessage;
import com.fitouts.shared.email.EmailService;
import com.fitouts.shared.email.EmailTemplateService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CommunicationEmailNotificationService {

    private static final int RATE_LIMIT_MINUTES = 2;
    private static final int EXCERPT_MAX = 280;

    private final CommunicationChannelRepository channelRepository;
    private final CommunicationChannelMemberRepository memberRepository;
    private final CommunicationOutboxRepository outboxRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoomRepository projectRoomRepository;
    private final RoomTaskRepository roomTaskRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final CommunicationService communicationService;

    @Value("${app.public-url:http://localhost:3002}")
    private String publicUrl;

    public CommunicationEmailNotificationService(
            CommunicationChannelRepository channelRepository,
            CommunicationChannelMemberRepository memberRepository,
            CommunicationOutboxRepository outboxRepository,
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            ProjectRoomRepository projectRoomRepository,
            RoomTaskRepository roomTaskRepository,
            EmailService emailService,
            EmailTemplateService emailTemplateService,
            @Lazy CommunicationService communicationService) {
        this.channelRepository = channelRepository;
        this.memberRepository = memberRepository;
        this.outboxRepository = outboxRepository;
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.projectRoomRepository = projectRoomRepository;
        this.roomTaskRepository = roomTaskRepository;
        this.emailService = emailService;
        this.emailTemplateService = emailTemplateService;
        this.communicationService = communicationService;
    }

    @Async
    @Transactional
    public void notifyChannelMessage(
            UUID channelUuid, UUID messageUuid, Long senderAccountId, String senderName, String body) {
        try {
            CommunicationChannel channel = channelRepository.findById(channelUuid).orElse(null);
            if (channel == null) {
                return;
            }
            dispatch(channel, messageUuid, senderAccountId, senderName, body);
        } catch (Exception e) {
            log.warn("Channel email notification failed for {}: {}", channelUuid, e.getMessage());
        }
    }

    @Async
    @Transactional
    public void notifyProjectRoomMessage(
            UUID projectRoomId, UUID messageUuid, Long senderAccountId, String senderName, String body) {
        try {
            ProjectRoom room = projectRoomRepository.findById(projectRoomId).orElse(null);
            if (room == null) {
                return;
            }
            communicationService.ensureChannelForProjectRoom(room);
            CommunicationChannel channel = channelRepository.findByProjectRoomIdOrderByCreatedAtAsc(projectRoomId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (channel == null) {
                return;
            }
            dispatch(channel, messageUuid, senderAccountId, senderName, body);
        } catch (Exception e) {
            log.warn("Room message email failed for room {}: {}", projectRoomId, e.getMessage());
        }
    }

    @Async
    @Transactional
    public void notifyRoomTaskMessage(
            UUID roomTaskId, UUID messageUuid, Long senderAccountId, String senderName, String body) {
        try {
            RoomTask task = roomTaskRepository.findById(roomTaskId).orElse(null);
            if (task == null) {
                return;
            }
            communicationService.ensureChannelForRoomTask(task);
            CommunicationChannel channel = channelRepository.findByRoomTaskIdOrderByCreatedAtAsc(roomTaskId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (channel == null) {
                return;
            }
            dispatch(channel, messageUuid, senderAccountId, senderName, body);
        } catch (Exception e) {
            log.warn("Task message email failed for task {}: {}", roomTaskId, e.getMessage());
        }
    }

    private void dispatch(
            CommunicationChannel channel,
            UUID messageUuid,
            Long senderAccountId,
            String senderName,
            String body) {
        if (isRateLimited(channel.getUuid(), senderAccountId)) {
            return;
        }

        String threadSubject = resolveThreadSubject(channel);
        String inReplyTo = resolveInReplyTo(channel);

        if (!StringUtils.hasText(channel.getEmailThreadRootId())) {
            channel.setEmailThreadRootId(emailService.generateMessageId());
            channel.setEmailThreadSubject(threadSubject);
            channelRepository.save(channel);
        }

        Set<String> recipientEmails = resolveRecipientEmails(channel, senderAccountId);
        String excerpt = excerpt(body);
        String link = communicationsUrl(channel.getUuid());
        String emailBody = emailTemplateService.render("communication-message", Map.of(
                "senderName", StringUtils.hasText(senderName) ? senderName : "A team member",
                "excerpt", excerpt,
                "conversationUrl", link));

        for (String recipient : recipientEmails) {
            try {
                String sentMessageId = emailService.send(EmailMessage.builder()
                        .to(recipient)
                        .subject(threadSubject)
                        .body(emailBody)
                        .html(true)
                        .messageId(emailService.generateMessageId())
                        .inReplyTo(inReplyTo)
                        .references(channel.getEmailThreadRootId())
                        .build());

                CommunicationOutbox outbox = new CommunicationOutbox();
                outbox.setChannelUuid(channel.getUuid());
                outbox.setSourceMessageUuid(messageUuid);
                outbox.setRecipientEmail(recipient);
                outbox.setSubject(threadSubject);
                outbox.setBody(excerpt);
                outbox.setSentBy(senderAccountId);
                outbox.setEmailMessageId(sentMessageId);
                outbox.setInReplyTo(inReplyTo);
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.warn("Failed comms email to {}: {}", recipient, e.getMessage());
            }
        }
    }

    private boolean isRateLimited(UUID channelUuid, Long senderAccountId) {
        return outboxRepository.findTopByChannelUuidAndSentByOrderBySentAtDesc(channelUuid, senderAccountId)
                .map(last -> last.getSentAt().isAfter(OffsetDateTime.now().minusMinutes(RATE_LIMIT_MINUTES)))
                .orElse(false);
    }

    private String resolveThreadSubject(CommunicationChannel channel) {
        if (StringUtils.hasText(channel.getEmailThreadSubject())) {
            return channel.getEmailThreadSubject();
        }
        String label = StringUtils.hasText(channel.getName()) ? channel.getName() : "Conversation";
        if (channel.getProjectId() != null) {
            Project project = projectRepository.findById(channel.getProjectId()).orElse(null);
            if (project != null && StringUtils.hasText(project.getName())) {
                label = project.getName() + " · " + label;
            }
        }
        return "[FitOuts] " + label;
    }

    private String resolveInReplyTo(CommunicationChannel channel) {
        return outboxRepository.findTopByChannelUuidOrderBySentAtDesc(channel.getUuid())
                .map(CommunicationOutbox::getEmailMessageId)
                .orElse(channel.getEmailThreadRootId());
    }

    private Set<String> resolveRecipientEmails(CommunicationChannel channel, Long senderAccountId) {
        Set<String> emails = new HashSet<>();
        List<CommunicationChannelMember> members = memberRepository.findByChannelUuid(channel.getUuid());
        for (CommunicationChannelMember member : members) {
            if (member.getAccountId() == null || member.getAccountId().equals(senderAccountId)) {
                continue;
            }
            accountRepository.findById(member.getAccountId())
                    .map(Account::getEmail)
                    .filter(StringUtils::hasText)
                    .ifPresent(email -> emails.add(email.trim().toLowerCase()));
        }
        if (channel.getProjectId() != null) {
            projectRepository.findById(channel.getProjectId()).ifPresent(project -> {
                if (project.getClientId() != null && !project.getClientId().equals(senderAccountId)) {
                    accountRepository.findById(project.getClientId())
                            .map(Account::getEmail)
                            .filter(StringUtils::hasText)
                            .ifPresent(email -> emails.add(email.trim().toLowerCase()));
                }
            });
        }
        return emails;
    }

    private String communicationsUrl(UUID channelUuid) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + "/admin/communications?channel=" + channelUuid;
    }

    private String excerpt(String body) {
        if (!StringUtils.hasText(body)) {
            return "(attachment or update)";
        }
        String trimmed = body.trim();
        return trimmed.length() <= EXCERPT_MAX ? trimmed : trimmed.substring(0, EXCERPT_MAX - 3) + "...";
    }
}
