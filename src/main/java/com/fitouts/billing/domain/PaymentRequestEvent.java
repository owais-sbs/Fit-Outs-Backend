package com.fitouts.billing.domain;

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
@Table(name = "payment_request_event")
@Getter
@Setter
public class PaymentRequestEvent {

    @Id
    private UUID uuid;

    @Column(name = "payment_request_uuid", nullable = false)
    private UUID paymentRequestUuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(nullable = false, length = 32)
    private String step;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(columnDefinition = "TEXT")
    private String comments;

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
    }
}
