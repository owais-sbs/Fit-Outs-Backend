package com.fitouts.subcontractor.domain;

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
@Table(name = "sc_award_boq_line")
@Getter
@Setter
public class ScAwardBoqLine {

    @Id
    private UUID uuid;

    @Column(name = "award_uuid", nullable = false)
    private UUID awardUuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "quote_uuid")
    private UUID quoteUuid;

    @Column(name = "quote_line_uuid")
    private UUID quoteLineUuid;

    @Column(name = "boq_line_id")
    private UUID boqLineId;

    @Column(name = "section_code", length = 64)
    private String sectionCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 32)
    private String unit;

    @Column(precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 18, scale = 4)
    private BigDecimal rate;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "line_status", nullable = false, length = 32)
    private String lineStatus = "QUOTED";

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
