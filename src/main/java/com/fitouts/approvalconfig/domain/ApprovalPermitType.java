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
@Table(name = "approval_permit_types")
@Getter
@Setter
public class ApprovalPermitType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "permit_code", nullable = false, length = 40)
    private String permitCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "issuing_body", length = 120)
    private String issuingBody;

    @Column(name = "typical_trigger", columnDefinition = "TEXT")
    private String typicalTrigger;

    @Column(name = "prerequisite_cases", columnDefinition = "TEXT")
    private String prerequisiteCases;

    @Column(name = "sla_working_days", length = 40)
    private String slaWorkingDays;

    @Column(name = "typical_validity", length = 80)
    private String typicalValidity;

    @Column(length = 80)
    private String deposit;

    @Column(length = 20)
    private String renewable;

    @Column(name = "blocks_activities", columnDefinition = "TEXT")
    private String blocksActivities;

    private boolean active = true;
    private boolean deleted = false;

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
