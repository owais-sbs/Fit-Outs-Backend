package com.fitouts.project.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "projects")
@Getter
@Setter
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "lead_reference_no", length = 64)
    private String leadReferenceNo;

    @Column(length = 30)
    private String status = "Planning";

    private Integer progress = 0;

    private BigDecimal budget;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "project_type", length = 100)
    private String projectType;

    @Column(name = "assigned_manager", length = 200)
    private String assignedManager;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    private boolean isActive = true;
    private boolean isDeleted = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
