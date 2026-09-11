package com.fitouts.subcontractor.domain;

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
@Table(name = "sc_package_bidder")
@Getter
@Setter
public class ScPackageBidder {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid", nullable = false)
    private UUID packageUuid;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "invited_at")
    private OffsetDateTime invitedAt;

    @Column(name = "viewed_at")
    private OffsetDateTime viewedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScBidderStatus status = ScBidderStatus.INVITED;

    @Column(name = "eligibility_json", columnDefinition = "TEXT")
    private String eligibilityJson;

    @Column(name = "regret_sent_at")
    private OffsetDateTime regretSentAt;

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
