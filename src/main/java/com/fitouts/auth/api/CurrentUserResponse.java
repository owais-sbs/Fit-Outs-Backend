package com.fitouts.auth.api;

import java.util.Set;

import com.fitouts.auth.domain.Role;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrentUserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String companyName;
    private Set<Role> roles;
}
