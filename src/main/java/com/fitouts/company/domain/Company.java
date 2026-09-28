package com.fitouts.company.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.fitouts.employee.domain.Feature;
import com.fitouts.subscription.domain.SubscriptionPlan;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

    @Serial
    private static final long serialVersionUID = -2862126059791512328L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(nullable = false)
    private String companyName;

    private String logo;

    @Column(name = "stamp_image_path", length = 512)
    private String stampImagePath;

    @Column(name = "signature_image_path", length = 512)
    private String signatureImagePath;

    @Column(nullable = true, unique = true, length = 100)
    private String domainSlug;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "subscription_plan_id", nullable = true)
    private SubscriptionPlan subscriptionPlan;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_enabled_features", joinColumns = @JoinColumn(name = "company_uuid"))
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false)
    private Set<Feature> enabledFeatures = new HashSet<>();

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
