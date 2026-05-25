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
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.tenant.application.TenantService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;

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
        if (request.getTenantUuid() != null) {
            account.setTenant(tenantService.getTenant(request.getTenantUuid()));
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
        return repository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Account> findOptionalByEmail(String email) {
        return repository.findByEmail(email.trim().toLowerCase());
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
                .tenantUuid(account.getTenant() != null ? account.getTenant().getUuid() : null)
                .active(account.getIsActive())
                .roles(account.getRoles())
                .build();
    }
}
