package com.fitouts.planning.domain;

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
@Table(name = "planning_gate_config")
@Getter
@Setter
public class PlanningGateConfig {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "require_material", nullable = false)
    private boolean requireMaterial;

    @Column(name = "require_resource", nullable = false)
    private boolean requireResource;

    @Column(name = "require_labour", nullable = false)
    private boolean requireLabour;

    @Column(name = "require_subcontractor", nullable = false)
    private boolean requireSubcontractor;

    @Column(name = "require_planning_ready", nullable = false)
    private boolean requirePlanningReady = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public static PlanningGateConfig defaults(UUID companyId) {
        PlanningGateConfig c = new PlanningGateConfig();
        c.setCompanyId(companyId);
        c.setRequireMaterial(false);
        c.setRequireResource(false);
        c.setRequireLabour(false);
        c.setRequireSubcontractor(false);
        c.setRequirePlanningReady(true);
        return c;
    }
}
