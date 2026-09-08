package com.fitouts.schedule.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "template_dependency")
@Getter
@Setter
public class TemplateDependency {

    @Id
    private UUID uuid;

    @Column(name = "template_uuid", nullable = false)
    private UUID templateUuid;

    @Column(name = "predecessor_code", nullable = false, length = 32)
    private String predecessorCode;

    @Column(name = "successor_code", nullable = false, length = 32)
    private String successorCode;

    @Column(nullable = false, length = 4)
    private String type = "FS";

    @Column(name = "lag_days", nullable = false)
    private int lagDays;

    /** Cure, test or strength-gain period. The compression engine may not shrink it. */
    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "lock_reason", columnDefinition = "text")
    private String lockReason;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
