package com.fitouts.checklist.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "site_visit_estimate_lines")
@Getter
@Setter
public class SiteVisitEstimateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_uuid", nullable = false)
    private SiteVisitEstimate estimate;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "room_type_id")
    private UUID roomTypeId;

    @Column(name = "floor_name", length = 120)
    private String floorName;

    @Column(name = "room_name", length = 120)
    private String roomName;

    @Column(length = 200)
    private String category;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal qty = BigDecimal.ONE;

    @Column(nullable = false, length = 32)
    private String unit = "LS";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "line_source", length = 32)
    private String lineSource;

    @Column(name = "scope_ref", length = 500)
    private String scopeRef;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void createTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = OffsetDateTime.now();
    }
}
