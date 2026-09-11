package com.fitouts.approvalconfig.domain;

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
@Table(name = "approval_company_registrations")
@Getter
@Setter
public class ApprovalCompanyRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "authority_id", nullable = false)
    private UUID authorityId;

    @Column(name = "reference_no", length = 120)
    private String referenceNo;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(nullable = false, length = 40)
    private String status = "Active";

    @Column(columnDefinition = "TEXT")
    private String notes;

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
