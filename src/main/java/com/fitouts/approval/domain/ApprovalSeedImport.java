package com.fitouts.approval.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "approval_seed_import")
@Getter
@Setter
public class ApprovalSeedImport {

    @Id
    private UUID uuid;

    @Column(nullable = false)
    private String source;

    @Column(name = "seed_version", length = 32)
    private String seedVersion;

    @Column(name = "imported_by")
    private Long importedBy;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        importedAt = OffsetDateTime.now();
    }
}
