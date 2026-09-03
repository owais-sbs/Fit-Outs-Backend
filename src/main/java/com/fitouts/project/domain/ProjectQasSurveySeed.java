package com.fitouts.project.domain;

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
@Table(name = "project_qas_survey_seed")
@Getter
@Setter
public class ProjectQasSurveySeed {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "source_estimate_uuid")
    private UUID sourceEstimateUuid;

    @Column(name = "floors_json", nullable = false, columnDefinition = "TEXT")
    private String floorsJson = "[]";

    @Column(name = "rooms_json", nullable = false, columnDefinition = "TEXT")
    private String roomsJson = "[]";

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
