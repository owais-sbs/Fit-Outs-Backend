package com.fitouts.account.api;

import java.util.Set;
import java.util.UUID;

import com.fitouts.auth.domain.Role;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String companyName;
    private UUID tenantUuid;
    private Boolean active;
    private Set<Role> roles;
}
