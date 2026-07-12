package com.fitouts.qto.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.drawing.domain.ProjectDrawing;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.enums.QtoSessionStatus;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "qto_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QtoSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drawing_id")
    private ProjectDrawing drawing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QtoSessionStatus status = QtoSessionStatus.DRAFT;

    @Column(name = "scale_ratio", precision = 14, scale = 8)
    private BigDecimal scaleRatio;

    @Column(name = "scale_unit", length = 10)
    @Builder.Default
    private String scaleUnit = "M";

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
