package com.fitouts.variation.domain;

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
@Table(name = "variation_event")
@Getter
@Setter
public class VariationEvent {

    @Id
    private UUID uuid;

    @Column(name = "variation_uuid", nullable = false)
    private UUID variationUuid;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "from_status", length = 40)
    private String fromStatus;

    @Column(name = "to_status", length = 40)
    private String toStatus;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "previous_contract_value", precision = 14, scale = 2)
    private BigDecimal previousContractValue;

    @Column(name = "new_contract_value", precision = 14, scale = 2)
    private BigDecimal newContractValue;

    @Column(name = "previous_margin", precision = 14, scale = 2)
    private BigDecimal previousMargin;

    @Column(name = "new_margin", precision = 14, scale = 2)
    private BigDecimal newMargin;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
