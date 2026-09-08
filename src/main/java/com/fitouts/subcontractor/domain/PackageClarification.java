package com.fitouts.subcontractor.domain;

import java.io.Serializable;
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
@Table(name = "package_clarifications")
@Getter
@Setter
public class PackageClarification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "subcontractor_id")
    private UUID subcontractorId; // Private clarification if not null

    @Column(name = "author_account_id", nullable = false)
    private Long authorAccountId;

    @Column(name = "question", columnDefinition = "text", nullable = false)
    private String question;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "answered_by")
    private Long answeredBy;

    @Column(name = "answered_at")
    private OffsetDateTime answeredAt;

    @Column(name = "is_public_addendum", nullable = false)
    private Boolean isPublicAddendum = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
