package com.fitouts.roomcollab.domain;

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
@Table(name = "room_messages")
@Getter
@Setter
public class RoomMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "project_room_id", nullable = false)
    private UUID projectRoomId;

    @Column(name = "sender_account_id", nullable = false)
    private Long senderAccountId;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "attachment_path", length = 1000)
    private String attachmentPath;

    @Column(name = "attachment_name", length = 500)
    private String attachmentName;

    @Column(name = "linked_task_id")
    private UUID linkedTaskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
