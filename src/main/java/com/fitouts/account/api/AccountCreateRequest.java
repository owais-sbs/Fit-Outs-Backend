package com.fitouts.account.api;

import java.util.Set;
import java.util.UUID;

import com.fitouts.auth.domain.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountCreateRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    private String phone;

    private String companyName;

    private UUID companyUuid;

    @NotEmpty
    private Set<Role> roles;
}
