package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "package_bidders")
@Getter
@Setter
public class PackageBidder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "status", nullable = false)
    private String status = "INVITED"; // INVITED, ACKNOWLEDGED, DECLINED, SUBMITTED

    @Column(name = "invited_at", nullable = false)
    private OffsetDateTime invitedAt;

    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (invitedAt == null) {
            invitedAt = OffsetDateTime.now();
        }
    }
}
