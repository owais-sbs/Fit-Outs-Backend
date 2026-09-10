package com.fitouts.subcontractor.domain;

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
@Table(name = "sc_community_registration")
@Getter
@Setter
public class ScCommunityRegistration {

    @Id
    private UUID uuid;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(name = "authority_code", nullable = false, length = 32)
    private String authorityCode;

    @Column(name = "authority_name", length = 160)
    private String authorityName;

    @Column(name = "registration_no", length = 120)
    private String registrationNo;

    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;

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
