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
@Table(name = "sc_package_attendance")
@Getter
@Setter
public class ScPackageAttendance {
    @Id
    private UUID uuid;
    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "responsibility_type", nullable = false, length = 48)
    private String responsibilityType;
    @Enumerated(EnumType.STRING)
    @Column(name = "responsible_party", nullable = false, length = 32)
    private ScAttendanceParty responsibleParty;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    @Column(name = "created_at", nullable = false, updatable = false)
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
