package com.fitouts.onboarding.application;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.application.AccessPhaseResolver;
import com.fitouts.auth.domain.AccessPhase;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.checklist.mapper.SiteVisitEstimateMapper;
import com.fitouts.company.application.CoverLetterBrandingService;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.employee.domain.Feature;
import com.fitouts.onboarding.api.CompanyProfileResponse;
import com.fitouts.onboarding.api.PaymentInstructionsResponse;
import com.fitouts.onboarding.api.SubmitPaymentRequest;
import com.fitouts.onboarding.api.SubmitPaymentResponse;
import com.fitouts.onboarding.config.ManualBillingProperties;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subscription.application.SubscriptionPlanService;
import com.fitouts.subscription.domain.SubscriptionPayment;
import com.fitouts.subscription.domain.SubscriptionPaymentRepository;
import com.fitouts.subscription.domain.SubscriptionPaymentStatus;
import com.fitouts.subscription.domain.SubscriptionPlan;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final SubscriptionPaymentRepository paymentRepository;
    private final FileStorageService fileStorageService;
    private final ManualBillingProperties billingProperties;

    public PaymentInstructionsResponse paymentInstructions() {
        return PaymentInstructionsResponse.builder()
                .bankName(billingProperties.getBankName())
                .accountName(billingProperties.getAccountName())
                .accountNumber(billingProperties.getAccountNumber())
                .iban(nullToEmpty(billingProperties.getIban()))
                .swift(nullToEmpty(billingProperties.getSwift()))
                .upiId(nullToEmpty(billingProperties.getUpiId()))
                .instructions(billingProperties.getInstructions())
                .build();
    }

    @Transactional
    public SubmitPaymentResponse submitPayment(
            AuthPrincipal principal,
            SubmitPaymentRequest request) {

        Account account = requireSelfServeAdmin(principal);
        AccessPhase phase = AccessPhaseResolver.resolve(account);
        if (phase != AccessPhase.SUBSCRIBE) {
            throw new ConflictException("Payment can only be submitted while awaiting a subscription");
        }

        SubscriptionPlan plan = subscriptionPlanService.getAssignablePlan(request.getPlanUuid());
        String billingCycle = request.getBillingCycle() == null
                ? "MONTHLY"
                : request.getBillingCycle().trim().toUpperCase(Locale.ROOT);
        BigDecimal amount = "ANNUAL".equals(billingCycle) ? plan.getPriceAnnual() : plan.getPriceMonthly();
        String paymentMethod = request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()
                ? "Bank transfer"
                : request.getPaymentMethod().trim();

        Company company = account.getCompany();
        if (company != null) {
            Optional<SubscriptionPayment> existingPending = paymentRepository
                    .findFirstByCompanyUuidAndStatusOrderByCreatedAtDesc(
                            company.getUuid(), SubscriptionPaymentStatus.PENDING);
            if (existingPending.isPresent()) {
                SubscriptionPayment pending = existingPending.get();
                return toSubmitResponse(pending, AccessPhase.SUBSCRIBE, true);
            }
        }

        if (company == null) {
            company = createTrialCompany(account);
            account.setCompany(company);
            account.setCompanyName(company.getCompanyName());
            accountRepository.save(account);
        } else if (company.getStatus() != CompanyStatus.TRIAL
                && company.getStatus() != CompanyStatus.ACTIVE) {
            throw new ConflictException("Company cannot accept a new subscription payment");
        }

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setCompany(company);
        payment.setPlan(plan);
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);
        payment.setStatus(SubscriptionPaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        return toSubmitResponse(payment, AccessPhase.SUBSCRIBE, false);
    }

    @Transactional
    public CompanyProfileResponse completeCompanyProfile(
            AuthPrincipal principal,
            String companyName,
            MultipartFile logoFile,
            MultipartFile stampFile,
            MultipartFile signatureFile) {

        Account account = requireSelfServeAdmin(principal);
        AccessPhase phase = AccessPhaseResolver.resolve(account);
        if (phase != AccessPhase.ONBOARDING && phase != AccessPhase.PORTAL) {
            throw new ForbiddenException("Complete payment before setting up your company profile");
        }

        Company company = account.getCompany();
        if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
            throw new ForbiddenException("Company subscription must be active");
        }

        if (companyName == null || companyName.isBlank()) {
            throw new BadRequestException("Company name is required");
        }
        if (logoFile == null || logoFile.isEmpty()) {
            if (company.getLogo() == null || company.getLogo().isBlank()) {
                throw new BadRequestException("Company logo is required");
            }
        } else {
            CoverLetterBrandingService.requireImage(logoFile);
            String previous = company.getLogo();
            company.setLogo(fileStorageService.store(logoFile, "company/" + company.getUuid() + "/logo"));
            fileStorageService.deleteIfExists(previous);
        }

        company.setCompanyName(companyName.trim());
        account.setCompanyName(company.getCompanyName());

        if (stampFile != null && !stampFile.isEmpty()) {
            CoverLetterBrandingService.requireImage(stampFile);
            String previous = company.getStampImagePath();
            company.setStampImagePath(
                    fileStorageService.store(stampFile, "cover-letter/" + company.getUuid()));
            fileStorageService.deleteIfExists(previous);
        }
        if (signatureFile != null && !signatureFile.isEmpty()) {
            CoverLetterBrandingService.requireImage(signatureFile);
            String previous = company.getSignatureImagePath();
            company.setSignatureImagePath(
                    fileStorageService.store(signatureFile, "cover-letter/" + company.getUuid()));
            fileStorageService.deleteIfExists(previous);
        }

        company.setOnboardingCompleted(true);
        companyRepository.save(company);
        accountRepository.save(account);

        return CompanyProfileResponse.builder()
                .companyName(company.getCompanyName())
                .logoUrl(SiteVisitEstimateMapper.toFileUrl(company.getLogo()))
                .onboardingCompleted(true)
                .build();
    }

    private Company createTrialCompany(Account account) {
        String placeholderName = account.getFullName().trim() + "'s Company";
        Company company = new Company();
        company.setCompanyName(placeholderName);
        company.setDomainSlug(allocateUniqueSlug(slugify(placeholderName)));
        company.setStatus(CompanyStatus.TRIAL);
        company.setOnboardingCompleted(false);
        company.setEnabledFeatures(new HashSet<>());
        return companyRepository.save(company);
    }

    private Account requireSelfServeAdmin(AuthPrincipal principal) {
        if (principal == null || principal.getEmail() == null) {
            throw new ForbiddenException("Authentication required");
        }
        Account account = accountRepository.findByEmailWithCompany(principal.getEmail())
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (!AccessPhaseResolver.isSelfServeAdmin(account)) {
            throw new ForbiddenException("Only company admins can complete subscription onboarding");
        }
        if (!account.getRoles().contains(Role.ADMIN)) {
            throw new ForbiddenException("Admin role required");
        }
        return account;
    }

    private SubmitPaymentResponse toSubmitResponse(
            SubscriptionPayment payment,
            AccessPhase accessPhase,
            boolean alreadyPending) {
        return SubmitPaymentResponse.builder()
                .paymentUuid(payment.getUuid())
                .companyUuid(payment.getCompany().getUuid())
                .planUuid(payment.getPlan().getUuid())
                .planName(payment.getPlan().getPlanName())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .accessPhase(accessPhase)
                .alreadyPending(alreadyPending)
                .build();
    }

    private String allocateUniqueSlug(String base) {
        String candidate = base;
        int suffix = 2;
        while (companyRepository.findByDomainSlugIgnoreCase(candidate).isPresent()) {
            candidate = base + "-" + suffix;
            suffix++;
            if (suffix > 1000) {
                throw new ConflictException("Unable to allocate a unique domain slug");
            }
        }
        return candidate;
    }

    private String slugify(String companyName) {
        String slug = companyName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            return "company-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Default feature set applied when a self-serve payment is marked paid. */
    public static Set<Feature> defaultEnabledFeatures() {
        return EnumSet.of(
                Feature.DASHBOARD,
                Feature.LEADS,
                Feature.SOURCES,
                Feature.CLIENTS,
                Feature.SITE_VISITS,
                Feature.CALENDAR,
                Feature.PROJECTS,
                Feature.SCHEDULE,
                Feature.SCHEDULE_TEMPLATES,
                Feature.APPROVALS,
                Feature.VALIDATION,
                Feature.VARIATIONS,
                Feature.CHECKLISTS,
                Feature.COMMUNICATIONS,
                Feature.VENDORS,
                Feature.DESIGN,
                Feature.QAS,
                Feature.BOQ,
                Feature.SUBCONTRACTOR_APPS,
                Feature.PROCUREMENT,
                Feature.PROJECT_CONFIG,
                Feature.EMPLOYEES,
                Feature.SETTINGS,
                Feature.TERMS,
                Feature.TASKS,
                Feature.ACTIVITIES,
                Feature.SNAGS,
                Feature.REPORTS);
    }
}
