package com.fitouts.approvalconfig.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_permit_cases")
@Getter
@Setter
public class ProjectPermitCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "permit_type_id", nullable = false)
    private UUID permitTypeId;

    @Column(name = "issuing_authority_id")
    private UUID issuingAuthorityId;

    @Column(nullable = false, length = 40)
    private String status = "Not started";

    @Column(name = "sla_working_days", length = 40)
    private String slaWorkingDays;

    @Column(name = "blocks_activities", columnDefinition = "TEXT")
    private String blocksActivities;

    @Column(name = "inclusion_rule", length = 40)
    private String inclusionRule;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
