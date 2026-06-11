package com.fitouts.auth.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fitouts.account.domain.Account;
import com.fitouts.auth.domain.Role;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthPrincipal implements AuthenticatedPrincipal, Serializable {

    private Long accountId;
    private UUID companyId;
    private String companyName;
    private String email;
    private String fullName;
    private Set<Role> roles;

    public static AuthPrincipal from(Account account) {
        return AuthPrincipal.builder()
                .accountId(account.getId())
                .companyId(account.getCompany() != null ? account.getCompany().getUuid() : null)
                .companyName(account.getCompany() != null ? account.getCompany().getCompanyName() : null)
                .email(account.getEmail())
                .fullName(account.getFullName())
                .roles(account.getRoles())
                .build();
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getName() {
        return email;
    }
}
