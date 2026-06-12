package com.fitouts.account.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.account.application.AccountService;
import com.fitouts.auth.domain.Role;
import com.fitouts.shared.api.BaseController;
import com.fitouts.shared.api.MessageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/accounts")
@Validated
@RequiredArgsConstructor
public class AccountController extends BaseController {

    private final AccountService service;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody AccountCreateRequest request) {
        try {
            return successResponse("Account created successfully", service.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to create account", exception.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            List<AccountResponse> accounts = service.getAll();
            return successResponse(accounts);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch accounts", exception.getMessage());
        }
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<?> getAllByRole(@PathVariable Role role) {
        try {
            List<AccountResponse> accounts = service.getAllByRole(role);
            return successResponse(accounts);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch accounts", exception.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return successResponse(service.getById(id));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch account", exception.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody AccountUpdateRequest request) {
        try {
            return successResponse("Account updated successfully", service.update(id, request));
        } catch (Exception exception) {
            return failureResponse("Unable to update account", exception.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return successResponse(new MessageResponse("Account deactivated successfully"));
        } catch (Exception exception) {
            return failureResponse("Unable to deactivate account", exception.getMessage());
        }
    }
}
