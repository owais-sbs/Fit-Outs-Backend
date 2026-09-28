package com.fitouts.subscription.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subscription.api.SubscriptionPaymentCreateRequest;
import com.fitouts.subscription.api.SubscriptionPaymentResponse;
import com.fitouts.subscription.domain.SubscriptionPayment;
import com.fitouts.subscription.domain.SubscriptionPaymentRepository;
import com.fitouts.subscription.domain.SubscriptionPaymentStatus;
import com.fitouts.subscription.domain.SubscriptionPlan;
import com.fitouts.subscription.domain.SubscriptionPlanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionPaymentService {

    private final SubscriptionPaymentRepository paymentRepository;
    private final CompanyRepository companyRepository;
    private final SubscriptionPlanRepository planRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<SubscriptionPaymentResponse> listAll(AuthPrincipal principal) {
        requireSuperAdmin(principal);
        return paymentRepository.findAllDetailed().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SubscriptionPaymentResponse create(SubscriptionPaymentCreateRequest request, AuthPrincipal principal) {
        requireSuperAdmin(principal);

        Company company = companyRepository.findById(request.getCompanyUuid())
                .orElseThrow(() -> new NotFoundException("Company not found"));
        SubscriptionPlan plan = planRepository.findById(request.getPlanUuid())
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));

        SubscriptionPaymentStatus status = parseStatus(request.getStatus(), SubscriptionPaymentStatus.PENDING);

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setCompany(company);
        payment.setPlan(plan);
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod().trim());
        payment.setStatus(status);

        if (status != SubscriptionPaymentStatus.PENDING) {
            applyDecision(payment, status, principal);
        }

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public SubscriptionPaymentResponse markPaid(UUID uuid, AuthPrincipal principal) {
        return decide(uuid, SubscriptionPaymentStatus.PAID, principal);
    }

    @Transactional
    public SubscriptionPaymentResponse markFailed(UUID uuid, AuthPrincipal principal) {
        return decide(uuid, SubscriptionPaymentStatus.FAILED, principal);
    }

    @Transactional
    public SubscriptionPaymentResponse cancel(UUID uuid, AuthPrincipal principal) {
        return decide(uuid, SubscriptionPaymentStatus.CANCELLED, principal);
    }

    private SubscriptionPaymentResponse decide(
            UUID uuid,
            SubscriptionPaymentStatus status,
            AuthPrincipal principal) {
        requireSuperAdmin(principal);
        SubscriptionPayment payment = paymentRepository.findDetailedById(uuid)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (payment.getStatus() != SubscriptionPaymentStatus.PENDING) {
            throw new ConflictException("Only pending payments can be decided");
        }
        applyDecision(payment, status, principal);
        return toResponse(paymentRepository.save(payment));
    }

    private void applyDecision(
            SubscriptionPayment payment,
            SubscriptionPaymentStatus status,
            AuthPrincipal principal) {
        payment.setStatus(status);
        payment.setDecidedAt(OffsetDateTime.now());
        if (principal.getAccountId() != null) {
            Account actor = accountRepository.findById(principal.getAccountId()).orElse(null);
            payment.setDecidedBy(actor);
        }

        Company company = payment.getCompany();
        if (status == SubscriptionPaymentStatus.PAID) {
            company.setSubscriptionPlan(payment.getPlan());
            company.setStatus(CompanyStatus.ACTIVE);
        } else if (status == SubscriptionPaymentStatus.FAILED
                || status == SubscriptionPaymentStatus.CANCELLED) {
            company.setStatus(CompanyStatus.SUSPENDED);
        }
        companyRepository.save(company);
    }

    private SubscriptionPaymentStatus parseStatus(String raw, SubscriptionPaymentStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return SubscriptionPaymentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid payment status: " + raw);
        }
    }

    private void requireSuperAdmin(AuthPrincipal principal) {
        if (principal == null
                || principal.getRoles() == null
                || !principal.getRoles().contains(Role.SUPER_ADMIN)) {
            throw new ForbiddenException("Super admin access required");
        }
    }

    private SubscriptionPaymentResponse toResponse(SubscriptionPayment payment) {
        Account decidedBy = payment.getDecidedBy();
        return SubscriptionPaymentResponse.builder()
                .uuid(payment.getUuid())
                .companyUuid(payment.getCompany().getUuid())
                .companyName(payment.getCompany().getCompanyName())
                .planUuid(payment.getPlan().getUuid())
                .planName(payment.getPlan().getPlanName())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .decidedAt(payment.getDecidedAt())
                .decidedByAccountId(decidedBy != null ? decidedBy.getId() : null)
                .decidedByName(decidedBy != null ? decidedBy.getFullName() : null)
                .build();
    }
}
