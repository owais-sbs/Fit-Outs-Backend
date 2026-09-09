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
@Table(name = "approval_authorities")
@Getter
@Setter
public class ApprovalAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(length = 80)
    private String emirate;

    @Column(name = "jurisdiction_areas", columnDefinition = "TEXT")
    private String jurisdictionAreas;

    @Column(name = "permits_issued", columnDefinition = "TEXT")
    private String permitsIssued;

    @Column(name = "submission_channel")
    private String submissionChannel;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "requires_company_registration", nullable = false)
    private boolean requiresCompanyRegistration = false;

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
