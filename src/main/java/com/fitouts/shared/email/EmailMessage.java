package com.fitouts.shared.email;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailMessage {
    private String to;
    private String subject;
    private String body;
    private boolean html;
    private List<EmailService.EmailAttachment> attachments;
    private String messageId;
    private String inReplyTo;
    private String references;
}
