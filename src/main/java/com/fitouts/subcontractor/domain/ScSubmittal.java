package com.fitouts.subcontractor.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sc_submittal")
@Getter
@Setter
public class ScSubmittal {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid")
    private UUID packageUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(nullable = false)
    private String title;

    @Column(name = "submittal_type", length = 64)
    private String submittalType;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo = 1;

    @Column(name = "review_code", length = 32)
    private String reviewCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScSubmittalStatus status = ScSubmittalStatus.SUBMITTED;

    @Column(name = "file_paths", columnDefinition = "TEXT")
    private String filePaths;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
