package com.fitouts.company.application;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.account.application.ClientPortalInviteService;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.company.api.CompanyCreateRequest;
import com.fitouts.company.api.CompanyResponse;
import com.fitouts.company.api.CompanyUpdateRequest;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.employee.domain.Feature;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.security.TemporaryPasswordGenerator;
import com.fitouts.subscription.application.SubscriptionPlanService;
import com.fitouts.subscription.domain.SubscriptionPlan;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CompanyService {

    private final CompanyRepository repository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final ClientPortalInviteService clientPortalInviteService;
    private final TransactionTemplate transactionTemplate;

    public CompanyService(
            CompanyRepository repository,
            SubscriptionPlanService subscriptionPlanService,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            FileStorageService fileStorageService,
            ClientPortalInviteService clientPortalInviteService,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.subscriptionPlanService = subscriptionPlanService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.clientPortalInviteService = clientPortalInviteService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CompanyResponse create(CompanyCreateRequest request) {
        return provision(
                request,
                null,
                null,
                null);
    }

    /**
     * Creates the company + admin account in a short transaction, then sends the
     * invite email after commit. SMTP success is reported via {@code inviteEmailSent};
     * mail failure no longer rolls back provisioning (matches employee/client invites).
     */
    public CompanyResponse provision(
            CompanyCreateRequest request,
            MultipartFile logoFile,
            MultipartFile stampFile,
            MultipartFile signatureFile) {

        ProvisionedAdmin provisioned = transactionTemplate.execute(status ->
                createCompanyAndAdmin(request, logoFile, stampFile, signatureFile));
        if (provisioned == null) {
            throw new BadRequestException("Unable to provision company");
        }

        boolean inviteSent = clientPortalInviteService.sendStaffPortalInvite(
                provisioned.accountId(),
                provisioned.fullName(),
                Role.ADMIN.displayLabel(),
                true);
        if (!inviteSent) {
            log.warn(
                    "Company {} provisioned but setup email to {} could not be queued",
                    provisioned.company().getUuid(),
                    provisioned.adminEmail());
        }

        return toResponse(provisioned.company(), provisioned.adminEmail(), null, inviteSent);
    }

    private ProvisionedAdmin createCompanyAndAdmin(
            CompanyCreateRequest request,
            MultipartFile logoFile,
            MultipartFile stampFile,
            MultipartFile signatureFile) {

        String companyName = request.getCompanyName().trim();
        String domainSlug = resolveUniqueSlug(request.getDomainSlug(), companyName);

        String adminEmail = request.getAdminEmail() != null
                ? request.getAdminEmail().trim().toLowerCase(Locale.ROOT)
                : null;
        if (adminEmail == null || adminEmail.isBlank()) {
            throw new BadRequestException("Admin email is required");
        }
        if (request.getSubscriptionPlanUuid() == null) {
            throw new BadRequestException("Subscription plan is required");
        }

        accountRepository.findByEmail(adminEmail).ifPresent(account -> {
            throw new ConflictException("Admin email already exists");
        });

        SubscriptionPlan plan = subscriptionPlanService.getAssignablePlan(request.getSubscriptionPlanUuid());

        Company company = new Company();
        company.setCompanyName(companyName);
        company.setDomainSlug(domainSlug);
        company.setStatus(CompanyStatus.ACTIVE);
        company.setSubscriptionPlan(plan);
        company.setEnabledFeatures(normalizeFeatures(request.getEnabledFeatures()));
        company.setOnboardingCompleted(true);

        company = repository.save(company);

        if (logoFile != null && !logoFile.isEmpty()) {
            CoverLetterBrandingService.requireImage(logoFile);
            company.setLogo(fileStorageService.store(logoFile, "company/" + company.getUuid() + "/logo"));
        } else if (request.getLogo() != null && !request.getLogo().isBlank()) {
            company.setLogo(request.getLogo().trim());
        }

        if (stampFile != null && !stampFile.isEmpty()) {
            CoverLetterBrandingService.requireImage(stampFile);
            company.setStampImagePath(
                    fileStorageService.store(stampFile, "cover-letter/" + company.getUuid()));
        }
        if (signatureFile != null && !signatureFile.isEmpty()) {
            CoverLetterBrandingService.requireImage(signatureFile);
            company.setSignatureImagePath(
                    fileStorageService.store(signatureFile, "cover-letter/" + company.getUuid()));
        }

        company = repository.save(company);

        String fullName = request.getAdminFullName() != null && !request.getAdminFullName().isBlank()
                ? request.getAdminFullName().trim()
                : companyName + " Admin";

        // Unusable placeholder until the admin completes the set-password invite.
        String placeholderPassword = TemporaryPasswordGenerator.generate();

        Account account = new Account();
        account.setFullName(fullName);
        account.setEmail(adminEmail);
        account.setPassword(passwordEncoder.encode(placeholderPassword));
        account.setCompanyName(companyName);
        account.setCompany(company);
        account.setIsActive(false);
        account.setRoles(new HashSet<>(Set.of(Role.ADMIN)));
        account = accountRepository.save(account);

        return new ProvisionedAdmin(company, account.getId(), adminEmail, fullName);
    }

    private record ProvisionedAdmin(
            Company company,
            Long accountId,
            String adminEmail,
            String fullName) {
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> getAll() {
        return repository.findAllWithPlan().stream()
                .map(company -> toResponse(company, null, null, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getByUuid(UUID uuid) {
        return toResponse(getCompany(uuid), null, null, null);
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
        if (request.getSubscriptionPlanUuid() != null) {
            company.setSubscriptionPlan(
                    subscriptionPlanService.getAssignablePlan(request.getSubscriptionPlanUuid()));
        }
        if (request.getStatus() != null) {
            company.setStatus(request.getStatus());
        }
        return toResponse(repository.save(company), null, null, null);
    }

    @Transactional
    public CompanyResponse activate(UUID uuid) {
        Company company = getCompany(uuid);
        company.setStatus(CompanyStatus.ACTIVE);
        return toResponse(repository.save(company), null, null, null);
    }

    @Transactional
    public CompanyResponse suspend(UUID uuid) {
        Company company = getCompany(uuid);
        if (company.getStatus() == CompanyStatus.TERMINATED) {
            throw new ConflictException("Terminated companies cannot be suspended");
        }
        company.setStatus(CompanyStatus.SUSPENDED);
        return toResponse(repository.save(company), null, null, null);
    }

    @Transactional
    public CompanyResponse terminate(UUID uuid) {
        Company company = getCompany(uuid);
        company.setStatus(CompanyStatus.TERMINATED);
        return toResponse(repository.save(company), null, null, null);
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

    private Set<Feature> normalizeFeatures(Set<Feature> features) {
        if (features == null || features.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(features);
    }

    private CompanyResponse toResponse(
            Company company,
            String adminEmail,
            String temporaryPassword,
            Boolean inviteEmailSent) {
        SubscriptionPlan plan = company.getSubscriptionPlan();
        return CompanyResponse.builder()
                .uuid(company.getUuid())
                .companyName(company.getCompanyName())
                .logo(company.getLogo())
                .stampImagePath(company.getStampImagePath())
                .signatureImagePath(company.getSignatureImagePath())
                .domainSlug(company.getDomainSlug())
                .subscriptionPlanUuid(plan != null ? plan.getUuid() : null)
                .subscriptionPlanName(plan != null ? plan.getPlanName() : null)
                .enabledFeatures(company.getEnabledFeatures() == null
                        ? Set.of()
                        : Set.copyOf(company.getEnabledFeatures()))
                .status(company.getStatus())
                .createdAt(company.getCreatedAt())
                .adminEmail(adminEmail)
                .temporaryPassword(temporaryPassword)
                .inviteEmailSent(inviteEmailSent)
                .build();
    }

    /**
     * Explicit slugs must be unique. Auto-generated slugs (from company name) get
     * a numeric suffix when the base is already taken — e.g. apex-legends-2 —
     * so retries after a partial provision don't fail on slug alone.
     */
    private String resolveUniqueSlug(String requestedSlug, String companyName) {
        String base = resolveSlug(requestedSlug, companyName);
        boolean explicit = requestedSlug != null && !requestedSlug.isBlank();
        if (explicit) {
            repository.findByDomainSlugIgnoreCase(base).ifPresent(existing -> {
                throw new ConflictException("Company domain slug already exists");
            });
            return base;
        }
        return allocateUniqueSlug(base);
    }

    private String allocateUniqueSlug(String base) {
        String candidate = base;
        int suffix = 2;
        while (repository.findByDomainSlugIgnoreCase(candidate).isPresent()) {
            candidate = base + "-" + suffix;
            suffix++;
            if (suffix > 1000) {
                throw new ConflictException("Unable to allocate a unique domain slug");
            }
        }
        return candidate;
    }

    private String resolveSlug(String requestedSlug, String companyName) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            return normalizeSlug(requestedSlug);
        }
        String slug = companyName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            throw new BadRequestException("Unable to generate domain slug from company name");
        }
        return slug;
    }

    private String normalizeSlug(String domainSlug) {
        return domainSlug.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
