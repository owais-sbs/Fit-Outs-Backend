package com.fitouts.shared.email;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fitouts.shared.error.BadRequestException;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String usernameFallback;

    @Value("${app.mail.from:}")
    private String configuredFrom;

    public void sendWithAttachments(
            String to,
            String subject,
            String body,
            List<EmailAttachment> attachments) {
        send(EmailMessage.builder()
                .to(to)
                .subject(subject)
                .body(body)
                .html(false)
                .attachments(attachments)
                .build());
    }

    public String send(EmailMessage message) {
        if (!StringUtils.hasText(message.getTo())) {
            throw new BadRequestException("Recipient email is required");
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            boolean hasAttachments = message.getAttachments() != null && !message.getAttachments().isEmpty();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, hasAttachments, "UTF-8");
            helper.setFrom(new InternetAddress(resolveFrom()));
            helper.setTo(message.getTo().trim());
            helper.setSubject(message.getSubject() != null ? message.getSubject() : "Fit-Outs Notification");
            helper.setText(message.getBody() != null ? message.getBody() : "", message.isHtml());
            if (hasAttachments) {
                for (EmailAttachment att : message.getAttachments()) {
                    if (att.data() != null && att.data().length > 0) {
                        helper.addAttachment(
                                att.filename(),
                                new ByteArrayResource(att.data()),
                                att.contentType());
                    }
                }
            }

            String messageId = StringUtils.hasText(message.getMessageId())
                    ? message.getMessageId()
                    : generateMessageId();
            mimeMessage.setHeader("Message-ID", messageId);
            if (StringUtils.hasText(message.getInReplyTo())) {
                mimeMessage.setHeader("In-Reply-To", message.getInReplyTo());
                mimeMessage.setHeader(
                        "References",
                        StringUtils.hasText(message.getReferences())
                                ? message.getReferences()
                                : message.getInReplyTo());
            }

            mailSender.send(mimeMessage);
            return messageId;
        } catch (Exception e) {
            log.error("Failed to send email to {}", message.getTo(), e);
            throw new BadRequestException("Failed to send email: " + e.getMessage());
        }
    }

    @Async
    public void sendAsync(EmailMessage message) {
        try {
            send(message);
        } catch (Exception e) {
            log.warn("Async email to {} failed: {}", message.getTo(), e.getMessage());
        }
    }

    public String generateMessageId() {
        return "<" + UUID.randomUUID() + "@fitouts.onepathsolutions.com>";
    }

    private String resolveFrom() {
        if (StringUtils.hasText(configuredFrom)) {
            return configuredFrom.trim();
        }
        if (StringUtils.hasText(usernameFallback)) {
            return usernameFallback.trim();
        }
        return "noreply@fitouts.local";
    }

    public record EmailAttachment(String filename, byte[] data, String contentType) {}
}
