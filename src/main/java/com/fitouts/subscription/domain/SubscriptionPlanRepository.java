package com.fitouts.subscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByPlanNameIgnoreCase(String planName);

    List<SubscriptionPlan> findByIsActiveTrue();
}
