package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "subcontractor_users")
@Getter
@Setter
public class SubcontractorUser implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "user_id", nullable = false)
    private Long userId; // References Account.id

    @Column(name = "role")
    private String role;

    @Column(name = "permissions_json", columnDefinition = "text")
    private String permissionsJson;

    @Column(name = "invited_by")
    private Long invitedBy; // References Account.id

    @Column(name = "status")
    private String status;
}
