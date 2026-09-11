package com.fitouts.schedule.domain;

import java.math.BigDecimal;
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
@Table(name = "activity_material_issue")
@Getter
@Setter
public class ActivityMaterialIssue {

    @Id
    private UUID uuid;

    @Column(name = "activity_uuid", nullable = false)
    private UUID activityUuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "progress_update_uuid")
    private UUID progressUpdateUuid;

    @Column(name = "plan_line_uuid")
    private UUID planLineUuid;

    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @Column(name = "material_name", length = 255)
    private String materialName;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qty = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityMaterialIssueStatus status = ActivityMaterialIssueStatus.DECLARED;

    @Column(name = "stock_movement_id")
    private UUID stockMovementId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (qty == null) {
            qty = BigDecimal.ZERO;
        }
        if (status == null) {
            status = ActivityMaterialIssueStatus.DECLARED;
        }
    }
}
