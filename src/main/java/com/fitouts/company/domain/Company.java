package com.fitouts.company.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.subscription.domain.SubscriptionPlan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(nullable = false)
    private String companyName;

    private String logo;

    @Column(nullable = true, unique = true, length = 100)
    private String domainSlug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private CompanyStatus status = CompanyStatus.TRIAL;

    @Column(nullable = true, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
