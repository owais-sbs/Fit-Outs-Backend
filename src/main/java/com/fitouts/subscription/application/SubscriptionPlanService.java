package com.fitouts.subscription.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subscription.api.SubscriptionPlanRequest;
import com.fitouts.subscription.api.SubscriptionPlanResponse;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository repository;

    @Transactional
    public SubscriptionPlanResponse create(SubscriptionPlanRequest request) {
        String planName = normalizePlanName(request.getPlanName());
        ensurePlanNameAvailable(planName, null);

        SubscriptionPlan plan = new SubscriptionPlan();
        apply(plan, request, planName);
        plan.setIsActive(request.getActive() == null || request.getActive());

        return toResponse(repository.save(plan));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanResponse getByUuid(UUID uuid) {
        return toResponse(getPlan(uuid));
    }

    @Transactional
    public SubscriptionPlanResponse update(UUID uuid, SubscriptionPlanRequest request) {
        SubscriptionPlan plan = getPlan(uuid);
        String planName = normalizePlanName(request.getPlanName());
        ensurePlanNameAvailable(planName, uuid);

        apply(plan, request, planName);
        if (request.getActive() != null) {
            plan.setIsActive(request.getActive());
        }

        return toResponse(repository.save(plan));
    }

    @Transactional
    public void deactivate(UUID uuid) {
        SubscriptionPlan plan = getPlan(uuid);
        plan.setIsActive(false);
        repository.save(plan);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getAssignablePlan(UUID uuid) {
        SubscriptionPlan plan = getPlan(uuid);
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new ConflictException("Inactive subscription plans cannot be assigned to new companies");
        }
        return plan;
    }

    private void apply(SubscriptionPlan plan, SubscriptionPlanRequest request, String planName) {
        plan.setPlanName(planName);
        plan.setMaxUsers(request.getMaxUsers());
        plan.setModulesIncluded(normalizeModules(request.getModulesIncluded()));
        plan.setPriceMonthly(request.getPriceMonthly());
        plan.setPriceAnnual(request.getPriceAnnual());
    }

    private Set<String> normalizeModules(Set<String> modulesIncluded) {
        Set<String> modules = new HashSet<>();
        modulesIncluded.forEach(module -> modules.add(module.trim()));
        return modules;
    }

    private String normalizePlanName(String planName) {
        return planName.trim();
    }

    private void ensurePlanNameAvailable(String planName, UUID excludedUuid) {
        repository.findByPlanNameIgnoreCase(planName)
                .filter(plan -> !plan.getUuid().equals(excludedUuid))
                .ifPresent(plan -> {
                    throw new ConflictException("Subscription plan name already exists");
                });
    }

    private SubscriptionPlan getPlan(UUID uuid) {
        return repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));
    }

    private SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return SubscriptionPlanResponse.builder()
                .uuid(plan.getUuid())
                .planName(plan.getPlanName())
                .maxUsers(plan.getMaxUsers())
                .modulesIncluded(plan.getModulesIncluded())
                .priceMonthly(plan.getPriceMonthly())
                .priceAnnual(plan.getPriceAnnual())
                .active(plan.getIsActive())
                .build();
    }
}
