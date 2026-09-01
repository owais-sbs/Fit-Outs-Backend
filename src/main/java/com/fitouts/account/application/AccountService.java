package com.fitouts.account.application;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.api.AccountCreateRequest;
import com.fitouts.account.api.AccountResponse;
import com.fitouts.account.api.AccountUpdateRequest;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.company.application.CompanyService;
import com.fitouts.lead.domain.Lead;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.shared.security.TemporaryPasswordGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyService companyService;

    @Transactional
    public AccountResponse create(AccountCreateRequest request) {
        repository.findByEmail(request.getEmail().trim().toLowerCase())
                .ifPresent(account -> {
                    throw new ConflictException("Email already exists");
                });

        Account account = new Account();
        account.setFullName(request.getFullName().trim());
        account.setEmail(request.getEmail().trim().toLowerCase());
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setPhone(request.getPhone());
        account.setCompanyName(request.getCompanyName());

        java.util.UUID companyUuid = request.getCompanyUuid();
        if (companyUuid == null) {
            companyUuid = CompanyContext.get();
        }
        if (companyUuid != null) {
            account.setCompany(companyService.getCompany(companyUuid));
        }

        account.setIsActive(true);
        account.setRoles(new HashSet<>(request.getRoles()));

        return toResponse(repository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllByRole(Role role) {
        java.util.UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return repository.findAllByCompanyUuidAndRole(companyId, role).stream()
                    .map(this::toResponse)
                    .toList();
        }
        return repository.findAll().stream()
                .filter(a -> a.getRoles().contains(role))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(Long id) {
        return toResponse(getAccount(id));
    }

    @Transactional
    public AccountResponse update(Long id, AccountUpdateRequest request) {
        Account account = getAccount(id);
        account.setFullName(request.getFullName().trim());
        account.setPhone(request.getPhone());
        account.setCompanyName(request.getCompanyName());
        account.setRoles(new HashSet<>(request.getRoles()));
        if (request.getActive() != null) {
            account.setIsActive(request.getActive());
        }
        return toResponse(repository.save(account));
    }

    @Transactional
    public void delete(Long id) {
        Account account = getAccount(id);
        account.setIsActive(false);
        repository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getAccountByEmail(String email) {
        return repository.findByEmailWithCompany(email.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Account> findOptionalByEmail(String email) {
        return repository.findByEmailWithCompany(email.trim().toLowerCase());
    }

    @Transactional
    public ClientAccountConversionResult createOrUpdateClientAccountForLead(Lead lead) {
        if (lead.getEmail() == null || lead.getEmail().isBlank()) {
            throw new BadRequestException("Lead email is required to create client account");
        }

        String email = lead.getEmail().trim().toLowerCase();
        Optional<Account> existingAccount = repository.findByEmail(email);
        if (existingAccount.isPresent()) {
            Account account = existingAccount.get();
            account.setIsActive(true);
            account.getRoles().add(Role.CLIENT);
            if (account.getCompany() == null) {
                if (lead.getCompanyEntity() != null) {
                    account.setCompany(lead.getCompanyEntity());
                } else if (CompanyContext.get() != null) {
                    account.setCompany(companyService.getCompany(CompanyContext.get()));
                }
            }
            if (account.getCompanyName() == null && account.getCompany() != null) {
                account.setCompanyName(account.getCompany().getCompanyName());
            }
            return new ClientAccountConversionResult(
                    false,
                    repository.save(account).getId(),
                    account.getEmail(),
                    null);
        }

        String temporaryPassword = TemporaryPasswordGenerator.generate();
        Account account = new Account();
        account.setFullName(requiredValue(lead.getClientName(), "Client"));
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode(temporaryPassword));
        account.setPhone(trimNullable(lead.getPhone()));
        account.setCompanyName(lead.getCompanyEntity() != null ? lead.getCompanyEntity().getCompanyName() : null);
        if (lead.getCompanyEntity() != null) {
            account.setCompany(lead.getCompanyEntity());
        } else if (CompanyContext.get() != null) {
            account.setCompany(companyService.getCompany(CompanyContext.get()));
            if (account.getCompanyName() == null) {
                account.setCompanyName(account.getCompany().getCompanyName());
            }
        }
        if (account.getCompany() == null) {
            throw new BadRequestException(
                    "Cannot create client account without a company. Sign in with a company-scoped admin account."
            );
        }
        account.setIsActive(true);
        account.setRoles(new HashSet<>(List.of(Role.CLIENT)));

        Account saved = repository.save(account);
        return new ClientAccountConversionResult(true, saved.getId(), saved.getEmail(), temporaryPassword);
    }

    /**
     * Creates or updates an account with SUBCONTRACTOR role for package appointment.
     */
    @Transactional
    public ClientAccountConversionResult createOrUpdateSubcontractorAccount(
            String fullName,
            String email,
            String phone,
            String companyName) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Subcontractor email is required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        Optional<Account> existingAccount = repository.findByEmail(normalizedEmail);
        if (existingAccount.isPresent()) {
            Account account = existingAccount.get();
            account.setIsActive(true);
            account.getRoles().add(Role.SUBCONTRACTOR);
            if (account.getCompany() == null && CompanyContext.get() != null) {
                account.setCompany(companyService.getCompany(CompanyContext.get()));
            }
            if (companyName != null && !companyName.isBlank()) {
                account.setCompanyName(companyName.trim());
            } else if (account.getCompanyName() == null && account.getCompany() != null) {
                account.setCompanyName(account.getCompany().getCompanyName());
            }
            if (phone != null && !phone.isBlank() && account.getPhone() == null) {
                account.setPhone(phone.trim());
            }
            if (fullName != null && !fullName.isBlank()
                    && (account.getFullName() == null || account.getFullName().isBlank())) {
                account.setFullName(fullName.trim());
            }
            return new ClientAccountConversionResult(
                    false,
                    repository.save(account).getId(),
                    account.getEmail(),
                    null);
        }

        if (CompanyContext.get() == null) {
            throw new BadRequestException(
                    "Cannot create subcontractor account without a company. Sign in with a company-scoped account."
            );
        }

        String temporaryPassword = TemporaryPasswordGenerator.generate();
        Account account = new Account();
        account.setFullName(requiredValue(fullName, "Subcontractor"));
        account.setEmail(normalizedEmail);
        account.setPassword(passwordEncoder.encode(temporaryPassword));
        account.setPhone(trimNullable(phone));
        account.setCompany(companyService.getCompany(CompanyContext.get()));
        account.setCompanyName(
                companyName != null && !companyName.isBlank()
                        ? companyName.trim()
                        : account.getCompany().getCompanyName());
        account.setIsActive(true);
        account.setRoles(new HashSet<>(List.of(Role.SUBCONTRACTOR)));

        Account saved = repository.save(account);
        return new ClientAccountConversionResult(true, saved.getId(), saved.getEmail(), temporaryPassword);
    }

    /**
     * Ensures an existing account can be appointed as a subcontractor (adds role if missing).
     */
    @Transactional
    public ClientAccountConversionResult ensureSubcontractorAccount(Long accountId, String companyName) {
        Account account = getAccount(accountId);
        account.setIsActive(true);
        account.getRoles().add(Role.SUBCONTRACTOR);
        if (companyName != null && !companyName.isBlank()) {
            account.setCompanyName(companyName.trim());
        }
        Account saved = repository.save(account);
        return new ClientAccountConversionResult(false, saved.getId(), saved.getEmail(), null);
    }

    private Account getAccount(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    public AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .fullName(account.getFullName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .companyName(account.getCompanyName())
                .companyUuid(account.getCompany() != null ? account.getCompany().getUuid() : null)
                .active(account.getIsActive())
                .roles(account.getRoles())
                .build();
    }

    private String requiredValue(String value, String fallback) {
        String trimmed = trimNullable(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
