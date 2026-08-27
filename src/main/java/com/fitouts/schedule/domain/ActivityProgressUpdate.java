package com.fitouts.schedule.domain;

import java.math.BigDecimal;
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
@Table(name = "activity_progress_update")
@Getter
@Setter
public class ActivityProgressUpdate {

    @Id
    private UUID uuid;

    @Column(name = "activity_uuid", nullable = false)
    private UUID activityUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "percent_complete", nullable = false)
    private int percentComplete;

    private String notes;

    @Column(name = "labour_hours")
    private BigDecimal labourHours;

    @Column(name = "photo_paths")
    private String photoPaths;

    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        reportedAt = OffsetDateTime.now();
    }
}
