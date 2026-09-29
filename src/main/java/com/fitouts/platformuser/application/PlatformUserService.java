package com.fitouts.platformuser.application;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.AuthSessionRecord;
import com.fitouts.auth.domain.AuthSessionRecordRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyRepository;
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.platformuser.api.PlatformUserResponse;
import com.fitouts.platformuser.domain.DeveloperSeedAccounts;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.subscription.domain.SubscriptionPayment;
import com.fitouts.subscription.domain.SubscriptionPaymentRepository;
import com.fitouts.subscription.domain.SubscriptionPaymentStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private static final int GRACE_PERIOD_DAYS = 7;

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final AuthSessionRecordRepository authSessionRecordRepository;

    @Transactional(readOnly = true)
    public List<PlatformUserResponse> list(String filter, AuthPrincipal principal) {
        requireSuperAdmin(principal);
        String normalized = filter == null ? "all" : filter.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "subscribers" -> accountRepository.findAllCompanyAdmins(Role.ADMIN, Role.SUPER_ADMIN).stream()
                    .filter(this::isSubscriber)
                    .filter(account -> account.getPurgeAt() == null)
                    .filter(account -> !DeveloperSeedAccounts.isCoreSeedEmail(account.getEmail()))
                    .map(this::toResponse)
                    .toList();
            case "deletion_queue" -> accountRepository
                    .findScheduledCompanyAdmins(OffsetDateTime.now(), Role.ADMIN, Role.SUPER_ADMIN)
                    .stream()
                    .map(this::toResponse)
                    .toList();
            case "developer_seed" -> accountRepository
                    .findAllByEmailInIgnoreCase(DeveloperSeedAccounts.coreSeedEmails()).stream()
                    .filter(account -> account.getPurgeAt() == null)
                    .map(this::toResponse)
                    .toList();
            case "all" -> accountRepository.findAllCompanyAdmins(Role.ADMIN, Role.SUPER_ADMIN).stream()
                    .filter(account -> account.getPurgeAt() == null)
                    .filter(account -> !DeveloperSeedAccounts.isCoreSeedEmail(account.getEmail()))
                    .map(this::toResponse)
                    .toList();
            default -> throw new BadRequestException("Unknown filter: " + filter);
        };
    }

    @Transactional
    public PlatformUserResponse scheduleDeletion(Long accountId, AuthPrincipal principal) {
        requireSuperAdmin(principal);
        Account target = getEligibleAdmin(accountId);
        if (target.getPurgeAt() != null) {
            throw new ConflictException("Account is already scheduled for deletion");
        }

        Account requestedBy = accountRepository.findById(principal.getAccountId())
                .orElseThrow(() -> new NotFoundException("Super admin account not found"));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime purgeAt = now.plusDays(GRACE_PERIOD_DAYS);

        Company company = target.getCompany();
        if (company != null) {
            company.setStatus(CompanyStatus.TERMINATED);
            companyRepository.save(company);
            List<Account> companyAccounts = accountRepository.findAllByCompanyUuid(company.getUuid());
            for (Account account : companyAccounts) {
                applyDeletionSchedule(account, now, purgeAt, requestedBy);
                revokeSessions(account);
            }
            return toResponse(accountRepository.findById(target.getId()).orElseThrow());
        }

        applyDeletionSchedule(target, now, purgeAt, requestedBy);
        revokeSessions(target);
        return toResponse(accountRepository.findById(target.getId()).orElseThrow());
    }

    @Transactional
    public PlatformUserResponse reactivate(Long accountId, AuthPrincipal principal) {
        requireSuperAdmin(principal);
        Account target = getEligibleAdmin(accountId);
        if (target.getPurgeAt() == null) {
            throw new ConflictException("Account is not scheduled for deletion");
        }
        if (!target.getPurgeAt().isAfter(OffsetDateTime.now())) {
            throw new ConflictException("Grace period has expired; account cannot be reactivated");
        }

        Company company = target.getCompany();
        if (company != null) {
            company.setStatus(CompanyStatus.ACTIVE);
            companyRepository.save(company);
            for (Account account : accountRepository.findAllByCompanyUuid(company.getUuid())) {
                clearDeletionSchedule(account);
            }
            return toResponse(accountRepository.findById(target.getId()).orElseThrow());
        }

        clearDeletionSchedule(target);
        return toResponse(accountRepository.findById(target.getId()).orElseThrow());
    }

    private void applyDeletionSchedule(
            Account account,
            OffsetDateTime scheduledAt,
            OffsetDateTime purgeAt,
            Account requestedBy) {
        account.setDeletionScheduledAt(scheduledAt);
        account.setPurgeAt(purgeAt);
        account.setDeletionRequestedBy(requestedBy);
        account.setIsActive(false);
        accountRepository.save(account);
    }

    private void clearDeletionSchedule(Account account) {
        account.setDeletionScheduledAt(null);
        account.setPurgeAt(null);
        account.setDeletionRequestedBy(null);
        account.setIsActive(true);
        accountRepository.save(account);
    }

    private void revokeSessions(Account account) {
        List<AuthSessionRecord> sessions = authSessionRecordRepository.findByAccountId(account.getId());
        OffsetDateTime now = OffsetDateTime.now();
        for (AuthSessionRecord session : sessions) {
            session.setRevokedAt(now);
            authSessionRecordRepository.save(session);
        }
    }

    private boolean isCompanyAdmin(Account account) {
        return account.getRoles() != null
                && account.getRoles().contains(Role.ADMIN)
                && !account.getRoles().contains(Role.SUPER_ADMIN);
    }

    private boolean isSubscriber(Account account) {
        Company company = account.getCompany();
        return company != null && paymentRepository.existsByCompanyUuid(company.getUuid());
    }

    private Account getEligibleAdmin(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (!isCompanyAdmin(account)) {
            throw new BadRequestException("Only company admin accounts can be managed here");
        }
        return account;
    }

    private PlatformUserResponse toResponse(Account account) {
        Company company = account.getCompany();
        UUID companyUuid = company != null ? company.getUuid() : null;
        String companyName = company != null ? company.getCompanyName() : account.getCompanyName();

        boolean subscriber = companyUuid != null && paymentRepository.existsByCompanyUuid(companyUuid);
        SubscriptionPaymentStatus latestPaymentStatus = null;
        if (companyUuid != null) {
            List<SubscriptionPayment> payments = paymentRepository.findAllByCompanyUuid(companyUuid);
            if (!payments.isEmpty()) {
                latestPaymentStatus = payments.get(0).getStatus();
            }
        }

        Long daysUntilPurge = null;
        if (account.getPurgeAt() != null) {
            daysUntilPurge = Math.max(0, ChronoUnit.DAYS.between(OffsetDateTime.now(), account.getPurgeAt()));
        }

        int affectedAccountCount = 1;
        if (companyUuid != null) {
            affectedAccountCount = accountRepository.findAllByCompanyUuid(companyUuid).size();
        }

        List<String> roles = account.getRoles() == null
                ? List.of()
                : account.getRoles().stream()
                        .map(Role::name)
                        .sorted()
                        .collect(Collectors.toList());

        return PlatformUserResponse.builder()
                .id(account.getId())
                .fullName(account.getFullName())
                .email(account.getEmail())
                .companyName(companyName)
                .companyUuid(companyUuid)
                .createdAt(account.getCreatedAt())
                .subscriber(subscriber)
                .latestPaymentStatus(latestPaymentStatus)
                .deletionScheduledAt(account.getDeletionScheduledAt())
                .purgeAt(account.getPurgeAt())
                .daysUntilPurge(daysUntilPurge)
                .affectedAccountCount(affectedAccountCount)
                .roles(roles)
                .deletable(isCompanyAdmin(account))
                .build();
    }

    private void requireSuperAdmin(AuthPrincipal principal) {
        if (principal == null
                || principal.getRoles() == null
                || !principal.getRoles().contains(Role.SUPER_ADMIN)) {
            throw new ForbiddenException("Super admin access required");
        }
    }
}
