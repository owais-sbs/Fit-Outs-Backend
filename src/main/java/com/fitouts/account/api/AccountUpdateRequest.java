package com.fitouts.account.api;

import java.util.Set;

import com.fitouts.auth.domain.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUpdateRequest {

    @NotBlank
    private String fullName;

    private String phone;

    private String companyName;

    @NotEmpty
    private Set<Role> roles;

    private Boolean active;
}
