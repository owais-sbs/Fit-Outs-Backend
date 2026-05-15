package com.fitouts.account.api;

import org.springframework.web.bind.annotation.*;

import com.fitouts.account.application.AccountService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService service;

    // Register
    @PostMapping("/register")
    public Object register(@RequestBody AccountRequest request) {

        return service.register(request);
    }

    // Login
    @PostMapping("/login")
    public Object login(@RequestBody LoginRequest request) {

        return service.login(request);
    }

    // Get All
    @GetMapping
    public Object getAll() {

        return service.getAll();
    }

    // Get By Id
    @GetMapping("/{id}")
    public Object getById(@PathVariable Long id) {

        return service.getById(id);
    }

    // Update
    @PutMapping("/{id}")
    public Object update(
            @PathVariable Long id,
            @RequestBody AccountRequest request) {

        return service.update(id, request);
    }

    // Delete
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {

        service.delete(id);

        return "Account deleted successfully";
    }
}