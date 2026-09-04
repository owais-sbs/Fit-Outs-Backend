package com.fitouts.projectdoc.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_document")
@Getter
@Setter
public class ProjectDocument {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String title;

    @Column
    private String category;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "published_to_client", nullable = false)
    private boolean publishedToClient;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "parent_document_uuid")
    private UUID parentDocumentUuid;

    /** e.g. DRAWING — when this library row mirrors another module's file. */
    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_uuid")
    private UUID sourceUuid;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
