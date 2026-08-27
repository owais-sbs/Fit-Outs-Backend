package com.fitouts.communications.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "communication_outbox")
@Getter
@Setter
public class CommunicationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "estimate_uuid")
    private UUID estimateUuid;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "channel_uuid")
    private UUID channelUuid;

    @Column(name = "source_message_uuid")
    private UUID sourceMessageUuid;

    @Column(name = "email_message_id", length = 255)
    private String emailMessageId;

    @Column(name = "in_reply_to", length = 255)
    private String inReplyTo;

    @Column(nullable = false, length = 20)
    private String status = "SENT";

    @PrePersist
    void onCreate() {
        if (sentAt == null) {
            sentAt = OffsetDateTime.now();
        }
    }
}
