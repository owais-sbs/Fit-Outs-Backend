package com.fitouts.auth.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fitouts.account.domain.Account;
import com.fitouts.auth.domain.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthPrincipal implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long accountId;
    private UUID companyId;
    private String companyName;
    private String email;
    private String fullName;
    private Set<Role> roles;

    public static AuthPrincipal from(Account account) {
        String companyName = account.getCompany() != null && account.getCompany().getCompanyName() != null
                ? account.getCompany().getCompanyName()
                : account.getCompanyName();
        Set<Role> roles = account.getRoles() == null ? new HashSet<>() : new HashSet<>(account.getRoles());
        return AuthPrincipal.builder()
                .accountId(account.getId())
                .companyId(account.getCompany() != null ? account.getCompany().getUuid() : null)
                .companyName(companyName)
                .email(account.getEmail())
                .fullName(account.getFullName())
                .roles(roles)
                .build();
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getName() {
        return email;
    }
}
