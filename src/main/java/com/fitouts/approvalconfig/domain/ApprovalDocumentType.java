package com.fitouts.approvalconfig.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "approval_document_types")
@Getter
@Setter
public class ApprovalDocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "doc_code", nullable = false, length = 20)
    private String docCode;

    @Column(nullable = false)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(name = "typically_required_for", columnDefinition = "TEXT")
    private String typicallyRequiredFor;

    @Column(name = "expiry_tracked", nullable = false)
    private boolean expiryTracked;

    @Column(name = "source_owner", length = 120)
    private String sourceOwner;

    private boolean active = true;
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
