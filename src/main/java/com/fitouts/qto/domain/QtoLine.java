package com.fitouts.qto.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.shared.enums.QtoLineSource;
import com.fitouts.shared.enums.QtoLineType;
import com.fitouts.workitemconfiguration.domain.WorkItem;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "qto_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QtoLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QtoSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 30)
    private QtoLineType lineType;

    @Column(length = 300)
    private String label;

    @Column(nullable = false, precision = 14, scale = 4)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String unit = "SQM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_item_id")
    private WorkItem workItem;

    @Column(precision = 12, scale = 2)
    private BigDecimal rate;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "geometry_json", columnDefinition = "TEXT")
    private String geometryJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QtoLineSource source = QtoLineSource.MANUAL;

    @Column(nullable = false)
    @Builder.Default
    private Boolean editable = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
