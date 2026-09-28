package com.fitouts.schedule.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

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
@Table(name = "schedule_activity_boq_line")
@Getter
@Setter
public class ScheduleActivityBoqLine {

    @Id
    private UUID uuid;

    @Column(name = "schedule_activity_uuid", nullable = false)
    private UUID scheduleActivityUuid;

    @Column(name = "boq_line_id", nullable = false)
    private UUID boqLineId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_source", nullable = false, length = 32)
    private BoqMatchSource matchSource;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
