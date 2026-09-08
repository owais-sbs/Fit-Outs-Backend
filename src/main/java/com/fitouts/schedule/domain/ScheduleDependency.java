package com.fitouts.schedule.domain;

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
@Table(name = "schedule_dependency")
@Getter
@Setter
public class ScheduleDependency {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "predecessor_uuid", nullable = false)
    private UUID predecessorUuid;

    @Column(name = "successor_uuid", nullable = false)
    private UUID successorUuid;

    /** FS, SS, FF or SF. */
    @Column(name = "dependency_type", nullable = false)
    private String dependencyType = "FS";

    @Column(name = "lag_working_days", nullable = false)
    private int lagWorkingDays;

    /**
     * A cure, test or strength-gain period. Compression and bar dragging must not reduce it,
     * so the engine treats the lag as a hard minimum.
     */
    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "lock_reason", columnDefinition = "text")
    private String lockReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
