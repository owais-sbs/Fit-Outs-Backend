package com.fitouts.completion.domain;

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
@Table(name = "project_closeout_carried_snag")
@Getter
@Setter
public class ProjectCloseoutCarriedSnag {

    @Id
    private UUID uuid;

    @Column(name = "checklist_uuid", nullable = false)
    private UUID checklistUuid;

    @Column(name = "snag_uuid", nullable = false)
    private UUID snagUuid;

    @Column(name = "carried_by")
    private Long carriedBy;

    @Column(name = "carried_at", nullable = false)
    private OffsetDateTime carriedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (carriedAt == null) carriedAt = OffsetDateTime.now();
    }
}
