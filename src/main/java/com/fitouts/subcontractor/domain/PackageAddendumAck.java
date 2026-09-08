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
@Table(name = "package_addendum_acks")
@Getter
@Setter
public class PackageAddendumAck implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "addendum_id", nullable = false)
    private UUID addendumId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "acknowledged_at", nullable = false)
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (acknowledgedAt == null) {
            acknowledgedAt = OffsetDateTime.now();
        }
    }
}
