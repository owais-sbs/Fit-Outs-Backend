package com.fitouts.account.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fitouts.account.api.AccountRequest;
import com.fitouts.account.api.LoginRequest;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;

    // Register
    public Account register(AccountRequest request) {

        repository.findByEmail(request.getEmail())
                .ifPresent(x -> {
                    throw new RuntimeException("Email already exists");
                });

        Account account = new Account();

        account.setFullName(request.getFullName());
        account.setEmail(request.getEmail());
        account.setPassword(request.getPassword());
        account.setPhone(request.getPhone());
        account.setCompanyName(request.getCompanyName());

        return repository.save(account);
    }

    // Login
    public Account login(LoginRequest request) {

        Account account = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email"));

        if (!account.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return account;
    }

    // Get All
    public List<Account> getAll() {
        return repository.findAll();
    }

    // Get By Id
    public Account getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    // Update
    public Account update(Long id, AccountRequest request) {

        Account account = getById(id);

        account.setFullName(request.getFullName());
        account.setPhone(request.getPhone());
        account.setCompanyName(request.getCompanyName());

        return repository.save(account);
    }

    // Delete
    public void delete(Long id) {

        Account account = getById(id);

        account.setIsActive(false);

        repository.save(account);
    }
}