package com.fitouts.planning.domain;

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
@Table(name = "project_planning_status")
@Getter
@Setter
public class ProjectPlanningStatus {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_status", nullable = false)
    private PlanAreaStatus materialStatus = PlanAreaStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_status", nullable = false)
    private PlanAreaStatus resourceStatus = PlanAreaStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "labour_status", nullable = false)
    private PlanAreaStatus labourStatus = PlanAreaStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "subcontractor_status", nullable = false)
    private PlanAreaStatus subcontractorStatus = PlanAreaStatus.NOT_STARTED;

    @Column(name = "planning_ready", nullable = false)
    private boolean planningReady;

    @Column(name = "gantt_publish_allowed", nullable = false)
    private boolean ganttPublishAllowed;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
