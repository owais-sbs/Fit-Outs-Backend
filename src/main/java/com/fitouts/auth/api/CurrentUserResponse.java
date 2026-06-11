package com.fitouts.auth.api;

import java.util.Set;
import java.util.UUID;

import com.fitouts.auth.domain.Role;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrentUserResponse {

    private Long id;
    private UUID companyId;
    private String companyName;
    private String fullName;
    private String email;
    private String phone;
    private Set<Role> roles;
}
