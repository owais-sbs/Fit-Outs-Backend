package com.fitouts.planning.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "planning_decision_audit")
@Getter
@Setter
public class PlanningDecisionAudit {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "decision_type", nullable = false, length = 64)
    private String decisionType;

    @Column(name = "from_value")
    private String fromValue;

    @Column(name = "to_value")
    private String toValue;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;

    @Column
    private String notes;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (decidedAt == null) decidedAt = OffsetDateTime.now();
    }
}
