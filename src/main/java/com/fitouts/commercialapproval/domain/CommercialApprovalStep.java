package com.fitouts.commercialapproval.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.auth.domain.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "commercial_approval_step")
@Getter
@Setter
public class CommercialApprovalStep {

    @Id
    private UUID uuid;

    @Column(name = "band_uuid", nullable = false)
    private UUID bandUuid;

    @Column(name = "step_order", nullable = false)
    private int stepOrder = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStepMode mode = ApprovalStepMode.SEQUENTIAL;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours = 48;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalate_to_role", length = 40)
    private Role escalateToRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
