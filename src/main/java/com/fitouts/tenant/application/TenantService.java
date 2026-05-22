package com.fitouts.tenant.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subscription.application.SubscriptionPlanService;
import com.fitouts.tenant.api.TenantCreateRequest;
import com.fitouts.tenant.api.TenantResponse;
import com.fitouts.tenant.domain.Tenant;
import com.fitouts.tenant.domain.TenantRepository;
import com.fitouts.tenant.domain.TenantStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository repository;
    private final SubscriptionPlanService subscriptionPlanService;

    @Transactional
    public TenantResponse create(TenantCreateRequest request) {
        String domainSlug = normalizeSlug(request.getDomainSlug());
        repository.findByDomainSlugIgnoreCase(domainSlug).ifPresent(tenant -> {
            throw new ConflictException("Tenant domain slug already exists");
        });

        Tenant tenant = new Tenant();
        tenant.setCompanyName(request.getCompanyName().trim());
        tenant.setLogo(normalizeNullable(request.getLogo()));
        tenant.setDomainSlug(domainSlug);
        tenant.setSubscriptionPlan(subscriptionPlanService.getAssignablePlan(request.getSubscriptionPlanUuid()));
        tenant.setStatus(request.getStatus() == null ? TenantStatus.TRIAL : request.getStatus());
        return toResponse(repository.save(tenant));
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getByUuid(UUID uuid) {
        return toResponse(getTenant(uuid));
    }

    @Transactional
    public TenantResponse suspend(UUID uuid) {
        Tenant tenant = getTenant(uuid);
        if (tenant.getStatus() == TenantStatus.TERMINATED) {
            throw new ConflictException("Terminated tenants cannot be suspended");
        }
        tenant.setStatus(TenantStatus.SUSPENDED);
        return toResponse(repository.save(tenant));
    }

    @Transactional
    public TenantResponse terminate(UUID uuid) {
        Tenant tenant = getTenant(uuid);
        tenant.setStatus(TenantStatus.TERMINATED);
        return toResponse(repository.save(tenant));
    }

    @Transactional(readOnly = true)
    public Tenant getTenant(UUID uuid) {
        return repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
    }

    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.builder()
                .uuid(tenant.getUuid())
                .companyName(tenant.getCompanyName())
                .logo(tenant.getLogo())
                .domainSlug(tenant.getDomainSlug())
                .subscriptionPlanUuid(tenant.getSubscriptionPlan().getUuid())
                .status(tenant.getStatus())
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    private String normalizeSlug(String domainSlug) {
        return domainSlug.trim().toLowerCase();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
