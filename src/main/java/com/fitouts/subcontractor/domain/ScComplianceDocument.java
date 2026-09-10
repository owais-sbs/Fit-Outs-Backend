package com.fitouts.subcontractor.domain;

import java.time.LocalDate;
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
@Table(name = "sc_compliance_document")
@Getter
@Setter
public class ScComplianceDocument {

    @Id
    private UUID uuid;

    @Column(name = "profile_uuid", nullable = false)
    private UUID profileUuid;

    @Column(name = "organization_uuid")
    private UUID organizationUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private ScComplianceDocType documentType;

    @Column(name = "reference_no", length = 120)
    private String referenceNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

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
