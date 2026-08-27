package com.fitouts.checklist.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.checklist.domain.SiteVisitEstimate;
import com.fitouts.checklist.repository.SiteVisitEstimateRepository;
import com.fitouts.communications.domain.CommunicationOutbox;
import com.fitouts.communications.repository.CommunicationOutboxRepository;
import com.fitouts.shared.email.EmailMessage;
import com.fitouts.shared.email.EmailService;
import com.fitouts.shared.email.EmailTemplateService;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SiteVisitEstimateEmailService {

    private final SiteVisitEstimateRepository estimateRepository;
    private final SiteVisitService siteVisitService;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final CommunicationOutboxRepository outboxRepository;

    @Transactional
    public void sendToClient(
            UUID siteVisitUuid,
            String recipientEmail,
            String subject,
            String messageBody,
            List<MultipartFile> attachments) {
        siteVisitService.getSiteVisit(siteVisitUuid);
        SiteVisitEstimate estimate = estimateRepository.findBySiteVisitUuid(siteVisitUuid)
                .orElseThrow(() -> new NotFoundException("Estimate not found"));

        List<EmailService.EmailAttachment> emailAttachments = new ArrayList<>();
        if (attachments != null) {
            for (MultipartFile file : attachments) {
                if (file == null || file.isEmpty()) continue;
                try {
                    emailAttachments.add(new EmailService.EmailAttachment(
                            file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment.pdf",
                            file.getBytes(),
                            file.getContentType() != null ? file.getContentType() : "application/pdf"));
                } catch (Exception e) {
                    throw new BadRequestException("Failed to read attachment: " + file.getOriginalFilename());
                }
            }
        }

        String quoteNo = StringUtils.hasText(estimate.getQuoteNo()) ? estimate.getQuoteNo() : "Quotation";
        String plainMessage = StringUtils.hasText(messageBody)
                ? messageBody
                : "Please find attached our quotation and appendix documents.";
        List<String> messageLines = Arrays.asList(plainMessage.split("\\r?\\n"));
        String htmlBody = emailTemplateService.render("estimate-to-client", Map.of(
                "quoteNo", quoteNo,
                "messageLines", messageLines));

        emailService.send(EmailMessage.builder()
                .to(recipientEmail)
                .subject(subject != null ? subject : "Quotation - " + quoteNo)
                .body(htmlBody)
                .html(true)
                .attachments(emailAttachments)
                .build());

        CommunicationOutbox entry = new CommunicationOutbox();
        entry.setEstimateUuid(estimate.getUuid());
        entry.setRecipientEmail(recipientEmail.trim());
        entry.setSubject(subject != null ? subject : "Quotation");
        entry.setBody(plainMessage);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            entry.setSentBy(principal.getAccountId());
        }
        outboxRepository.save(entry);
    }
}
