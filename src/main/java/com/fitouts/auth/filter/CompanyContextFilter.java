package com.fitouts.auth.filter;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fitouts.account.application.AccountService;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.context.CompanyContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CompanyContextFilter extends OncePerRequestFilter {

    private final AccountService accountService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            AuthPrincipal principal = resolvePrincipal();
            if (principal != null && principal.getCompanyId() != null) {
                CompanyContext.set(principal.getCompanyId());
            }
            filterChain.doFilter(request, response);
        } finally {
            CompanyContext.clear();
        }
    }

    /**
     * DevTools + JDBC sessions can restore AuthPrincipal from another classloader,
     * so {@code instanceof} fails and company-scoped lists (projects) come back empty.
     */
    private AuthPrincipal resolvePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        String email = auth.getName();
        if (email == null || !email.contains("@")) {
            return null;
        }
        return accountService.findOptionalByEmail(email).map(account -> {
            AuthPrincipal principal = AuthPrincipal.from(account);
            UsernamePasswordAuthenticationToken restored = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(restored);
            return principal;
        }).orElse(null);
    }
}
