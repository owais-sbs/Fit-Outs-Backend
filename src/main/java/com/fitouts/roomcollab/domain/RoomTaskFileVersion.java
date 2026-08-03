package com.fitouts.roomcollab.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "room_task_file_versions")
@Getter
@Setter
public class RoomTaskFileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_role", nullable = false, length = 20)
    private FileUploaderRole uploaderRole;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "change_notes", columnDefinition = "TEXT")
    private String changeNotes;

    @Column(name = "is_final", nullable = false)
    private Boolean isFinal = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileVersionStatus status = FileVersionStatus.SUBMITTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        if (isFinal == null) isFinal = false;
        if (status == null) status = FileVersionStatus.SUBMITTED;
    }
}
