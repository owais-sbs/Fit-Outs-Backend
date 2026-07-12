package com.fitouts.boq.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fitouts.qto.domain.QtoLine;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "boq_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boq_id", nullable = false)
    private BoqDocument boq;

    @Column(name = "category_code", length = 20)
    private String categoryCode;

    @Column(name = "category_name", length = 200)
    private String categoryName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String unit;

    @Column(nullable = false, precision = 14, scale = 4)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qto_line_id")
    private QtoLine qtoLine;

    @Column(name = "floor_label", length = 100)
    private String floorLabel;

    @Column(name = "room_label", length = 200)
    private String roomLabel;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(length = 30)
    @Builder.Default
    private String source = "QTO";

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
