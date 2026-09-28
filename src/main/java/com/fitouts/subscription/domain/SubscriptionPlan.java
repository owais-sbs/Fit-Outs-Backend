package com.fitouts.subscription.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
public class SubscriptionPlan implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String planName;

    @Column(nullable = false)
    private Integer maxUsers;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscription_plan_modules", joinColumns = @JoinColumn(name = "plan_uuid"))
    @Column(name = "module_name", nullable = false)
    private Set<String> modulesIncluded = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscription_plan_features", joinColumns = @JoinColumn(name = "plan_uuid"))
    @OrderColumn(name = "display_order")
    @Column(name = "feature_text", nullable = false, length = 500)
    private List<String> features = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMonthly;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAnnual;

    @Column(nullable = false)
    private Boolean isActive = true;
}
