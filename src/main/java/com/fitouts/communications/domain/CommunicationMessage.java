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
@Table(name = "communication_messages")
@Getter
@Setter
public class CommunicationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "channel_uuid", nullable = false)
    private UUID channelUuid;

    @Column(name = "sender_account_id", nullable = false)
    private Long senderAccountId;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "attachment_path", length = 512)
    private String attachmentPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
