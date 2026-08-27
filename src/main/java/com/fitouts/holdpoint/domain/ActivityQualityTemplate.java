package com.fitouts.holdpoint.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "activity_quality_template")
@IdClass(ActivityQualityTemplateId.class)
@Getter
@Setter
public class ActivityQualityTemplate {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Id
    @Column(name = "activity_type", length = 100)
    private String activityType;

    @Column(name = "checklist_json")
    private String checklistJson;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
