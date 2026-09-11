package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "sc_quote")
@Getter
@Setter
public class ScQuote {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "total_value", precision = 18, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "exclusions_text", columnDefinition = "TEXT")
    private String exclusionsText;

    @Column(name = "qualifications_text", columnDefinition = "TEXT")
    private String qualificationsText;

    @Column(name = "validity_date")
    private LocalDate validityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScQuoteStatus status = ScQuoteStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
