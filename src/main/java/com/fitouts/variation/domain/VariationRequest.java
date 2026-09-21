package com.fitouts.variation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "variation_request")
@Getter
@Setter
public class VariationRequest {

    @Id
    private UUID uuid;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "cr_number", nullable = false, length = 32)
    private String crNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VariationOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VariationStatus status = VariationStatus.DRAFT;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 40)
    private VariationReasonCode reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_mode", nullable = false, length = 20)
    private VariationCostMode costMode = VariationCostMode.LUMP_SUM;

    @Column(name = "sell_delta", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellDelta = BigDecimal.ZERO;

    @Column(name = "cost_delta", nullable = false, precision = 14, scale = 2)
    private BigDecimal costDelta = BigDecimal.ZERO;

    @Column(name = "proposed_delay_days")
    private Integer proposedDelayDays;

    @Column(name = "apply_schedule_on_approval", nullable = false)
    private boolean applyScheduleOnApproval;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "raised_by")
    private Long raisedBy;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "triage_note", columnDefinition = "TEXT")
    private String triageNote;

    @Column(name = "reject_comment", columnDefinition = "TEXT")
    private String rejectComment;

    @Column(name = "approval_run_uuid")
    private UUID approvalRunUuid;

    @Column(name = "source_boq_id")
    private UUID sourceBoqId;

    @Column(name = "result_boq_id")
    private UUID resultBoqId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
