package com.fitouts.notification.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An in-app alert. Expiry and SLA warnings need somewhere to land besides email,
 * and the existing notifications page has no backing store.
 */
@Entity
@Table(name = "in_app_notification")
@Getter
@Setter
public class InAppNotification {

    @Id
    private UUID uuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** Null for a company-wide alert that anyone with access should see. */
    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 48)
    private String category;

    @Column(nullable = false, length = 16)
    private String severity = "INFO";

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "link_path", length = 255)
    private String linkPath;

    @Column(name = "source_type", length = 48)
    private String sourceType;

    @Column(name = "source_uuid")
    private UUID sourceUuid;

    /**
     * Stops the daily expiry sweep re-alerting the same permit at the same threshold.
     * Unique when present.
     */
    @Column(name = "dedupe_key", length = 180)
    private String dedupeKey;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
