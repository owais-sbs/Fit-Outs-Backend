package com.fitouts.boq.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.project.domain.Project;
import com.fitouts.qto.domain.QtoSession;
import com.fitouts.shared.enums.BoqDocumentStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "boq_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qto_session_id")
    private QtoSession qtoSession;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String version = "1.0";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_boq_id")
    private BoqDocument parentBoq;

    @Column(name = "revision_label", length = 100)
    private String revisionLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BoqDocumentStatus status = BoqDocumentStatus.DRAFT;

    @Column(name = "current_approval_step", length = 30)
    private String currentApprovalStep;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "last_rejection_comment", columnDefinition = "TEXT")
    private String lastRejectionComment;

    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "vat_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
