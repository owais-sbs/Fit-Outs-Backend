package com.fitouts.approval.domain;

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
@Table(name = "document_type")
@Getter
@Setter
public class DocumentType {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 48)
    private String category;

    @Column(name = "typically_required_for", columnDefinition = "TEXT")
    private String typicallyRequiredFor;

    @Column(name = "is_expiry_tracked", nullable = false)
    private boolean expiryTracked = false;

    @Column(name = "owner_role", length = 64)
    private String ownerRole;

    @Column(name = "source_owner", length = 120)
    private String sourceOwner;

    @Column(name = "template_file_path", columnDefinition = "TEXT")
    private String templateFilePath;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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
