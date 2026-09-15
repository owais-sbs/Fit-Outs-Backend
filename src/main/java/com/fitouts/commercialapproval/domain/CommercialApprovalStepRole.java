package com.fitouts.commercialapproval.domain;

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
@Table(name = "commercial_approval_step_role")
@Getter
@Setter
public class CommercialApprovalStepRole {

    @Id
    private UUID uuid;

    @Column(name = "step_uuid", nullable = false)
    private UUID stepUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Role role;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
