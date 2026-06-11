package com.fitouts.company.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.AccountRepository;
import com.fitouts.company.api.CompanyCreateRequest;
import com.fitouts.company.api.CompanyResponse;
import com.fitouts.company.api.CompanyUpdateRequest;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subscription.application.SubscriptionPlanService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository repository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AccountRepository accountRepository;

    @Transactional
    public CompanyResponse create(CompanyCreateRequest request) {
        String domainSlug = normalizeSlug(request.getDomainSlug());
        repository.findByDomainSlugIgnoreCase(domainSlug).ifPresent(company -> {
            throw new ConflictException("Company domain slug already exists");
        });

        Company company = new Company();
        company.setCompanyName(request.getCompanyName().trim());
        company.setLogo(normalizeNullable(request.getLogo()));
        company.setDomainSlug(domainSlug);
        company.setSubscriptionPlan(subscriptionPlanService.getAssignablePlan(request.getSubscriptionPlanUuid()));
        company.setStatus(request.getStatus() == null ? CompanyStatus.TRIAL : request.getStatus());
        return toResponse(repository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getByUuid(UUID uuid) {
        return toResponse(getCompany(uuid));
    }

    @Transactional
    public CompanyResponse update(UUID uuid, CompanyUpdateRequest request) {
        Company company = getCompany(uuid);
        String domainSlug = normalizeSlug(request.getDomainSlug());
        repository.findByDomainSlugIgnoreCase(domainSlug)
                .filter(existing -> !existing.getUuid().equals(uuid))
                .ifPresent(existing -> {
                    throw new ConflictException("Company domain slug already exists");
                });

        company.setCompanyName(request.getCompanyName().trim());
        company.setLogo(normalizeNullable(request.getLogo()));
        company.setDomainSlug(domainSlug);
        company.setSubscriptionPlan(subscriptionPlanService.getAssignablePlan(request.getSubscriptionPlanUuid()));
        company.setStatus(request.getStatus());
        return toResponse(repository.save(company));
    }

    @Transactional
    public CompanyResponse suspend(UUID uuid) {
        Company company = getCompany(uuid);
        if (company.getStatus() == CompanyStatus.TERMINATED) {
            throw new ConflictException("Terminated companies cannot be suspended");
        }
        company.setStatus(CompanyStatus.SUSPENDED);
        return toResponse(repository.save(company));
    }

    @Transactional
    public CompanyResponse terminate(UUID uuid) {
        Company company = getCompany(uuid);
        company.setStatus(CompanyStatus.TERMINATED);
        return toResponse(repository.save(company));
    }

    @Transactional
    public void delete(UUID uuid) {
        if (!accountRepository.findAllByCompanyUuid(uuid).isEmpty()) {
            throw new ConflictException("Company with accounts cannot be deleted");
        }
        repository.delete(getCompany(uuid));
    }

    @Transactional(readOnly = true)
    public Company getCompany(UUID uuid) {
        return repository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .uuid(company.getUuid())
                .companyName(company.getCompanyName())
                .logo(company.getLogo())
                .domainSlug(company.getDomainSlug())
                .subscriptionPlanUuid(company.getSubscriptionPlan().getUuid())
                .status(company.getStatus())
                .createdAt(company.getCreatedAt())
                .build();
    }

    private String normalizeSlug(String domainSlug) {
        return domainSlug.trim().toLowerCase();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
