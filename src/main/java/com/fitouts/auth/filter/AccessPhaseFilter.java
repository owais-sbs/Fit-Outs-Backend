package com.fitouts.auth.filter;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fitouts.account.application.AccountService;
import com.fitouts.account.domain.Account;
import com.fitouts.auth.application.AccessPhaseResolver;
import com.fitouts.auth.domain.AccessPhase;
import com.fitouts.auth.security.AuthPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Blocks product APIs until a self-serve admin finishes payment + company onboarding.
 */
@Component
@RequiredArgsConstructor
public class AccessPhaseFilter extends OncePerRequestFilter {

    private final AccountService accountService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthPrincipal principal = resolvePrincipal();
        if (principal == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Account account = accountService.findOptionalByEmail(principal.getEmail()).orElse(null);
        if (account == null) {
            filterChain.doFilter(request, response);
            return;
        }

        AccessPhase phase = AccessPhaseResolver.resolve(account);
        if (phase == AccessPhase.PORTAL) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Complete subscription onboarding to access this resource\","
                        + "\"accessPhase\":\"" + phase.name() + "\"}");
    }

    private boolean isExempt(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/onboarding/")
                || path.startsWith("/api/public/")
                || path.startsWith("/api/ws/");
    }

    private AuthPrincipal resolvePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }
}
