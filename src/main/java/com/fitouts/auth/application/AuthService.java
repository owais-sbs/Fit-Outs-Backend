package com.fitouts.auth.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.application.AccountService;
import com.fitouts.account.domain.Account;
import com.fitouts.auth.api.AuthSessionResponse;
import com.fitouts.auth.api.CurrentUserResponse;
import com.fitouts.auth.api.LoginRequest;
import com.fitouts.auth.api.LoginResponse;
import com.fitouts.auth.api.VerifyOtpRequest;
import com.fitouts.auth.config.AuthProperties;
import com.fitouts.auth.domain.OtpChallenge;
import com.fitouts.auth.domain.RememberedDevice;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountService accountService;
    private final DeviceService deviceService;
    private final OtpService otpService;
    private final AuthProperties authProperties;
    private final SecurityContextRepository securityContextRepository;
    private final SessionTrackingService sessionTrackingService;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CookieService cookieService;

    @Transactional
    public LoginResult login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        Account account = accountService.findOptionalByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new UnauthorizedException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        RememberedDevice device = deviceService.resolveDevice(account, servletRequest, servletResponse);
        // Role-based OTP check disabled
        // if (requiresOtp(account)) {
        //     OtpService.GeneratedOtp generatedOtp = otpService.createChallenge(account, device);
        //     return new LoginResult(true, LoginResponse.builder()
        //             .status("OTP_REQUIRED")
        //             .message("OTP verification required")
        //             .challengeId(generatedOtp.challenge().getChallengeId())
        //             .otp(authProperties.getOtp().isDevExposeValue() ? generatedOtp.rawOtp() : null)
        //             .build());
        // }

        return new LoginResult(false, authenticate(account, device, servletRequest, servletResponse));
    }

    @Transactional
    public LoginResponse verifyOtp(
            VerifyOtpRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        OtpChallenge challenge = otpService.verify(request.getChallengeId(), request.getOtp());
        if (!Boolean.TRUE.equals(challenge.getAccount().getIsActive())) {
            throw new UnauthorizedException("Account is inactive");
        }
        return authenticate(challenge.getAccount(), challenge.getDevice(), servletRequest, servletResponse);
    }

    public CurrentUserResponse me(AuthPrincipal principal) {
        Account account = accountService.getAccountByEmail(principal.getEmail());
        return toCurrentUser(account);
    }

    public List<AuthSessionResponse> getSessions(AuthPrincipal principal, HttpServletRequest request) {
        String currentSessionId = request.getSession(false) != null ? request.getSession(false).getId() : null;
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principal.getEmail());
        return sessions.values().stream()
                .map(session -> {
                    var record = sessionTrackingService.getRecord(session.getId());
                    if (record != null && !record.getAccount().getId().equals(principal.getAccountId())) {
                        record = null;
                    }
                    return AuthSessionResponse.builder()
                            .sessionId(session.getId())
                            .deviceId(record != null && record.getDevice() != null ? record.getDevice().getId() : null)
                            .deviceLabel(record != null && record.getDevice() != null ? record.getDevice().getLabel() : null)
                            .userAgent(record != null && record.getDevice() != null ? record.getDevice().getUserAgent() : null)
                            .createdAt(record != null ? record.getCreatedAt() : null)
                            .lastSeenAt(record != null ? record.getLastSeenAt() : null)
                            .trustedUntil(record != null && record.getDevice() != null ? record.getDevice().getTrustedUntil() : null)
                            .current(session.getId().equals(currentSessionId))
                            .build();
                })
                .sorted(Comparator.comparing(AuthSessionResponse::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public void revokeSession(
            AuthPrincipal principal,
            String sessionId,
            HttpServletRequest request,
            HttpServletResponse response) {

        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principal.getEmail());
        if (!sessions.containsKey(sessionId)) {
            throw new ForbiddenException("Session does not belong to the current user");
        }

        sessionRepository.deleteById(sessionId);
        sessionTrackingService.revoke(sessionId);

        if (request.getSession(false) != null && sessionId.equals(request.getSession(false).getId())) {
            logout(request, response, principal);
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, AuthPrincipal principal) {
        if (request.getSession(false) != null) {
            sessionTrackingService.revoke(request.getSession(false).getId());
        }

        new SecurityContextLogoutHandler().logout(request, response, null);
        SecurityContextHolder.clearContext();
        cookieService.clearSessionCookie(response);
    }

    private LoginResponse authenticate(
            Account account,
            RememberedDevice device,
            HttpServletRequest request,
            HttpServletResponse response) {

        AuthPrincipal principal = AuthPrincipal.from(account);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        securityContextRepository.saveContext(context, request, response);

        if (request.getSession(false) == null) {
            throw new IllegalStateException("Session was not created");
        }

        sessionTrackingService.registerSession(request.getSession(false).getId(), account, device);
        return LoginResponse.builder()
                .status("AUTHENTICATED")
                .message("Login successful")
                .user(toCurrentUser(account))
                .build();
    }

    // private boolean requiresOtp(Account account) {
    //     return authProperties.getOtp().isSuperAdminEnabled() && account.getRoles().contains(Role.SUPER_ADMIN);
    // }

    private CurrentUserResponse toCurrentUser(Account account) {
        return CurrentUserResponse.builder()
                .id(account.getId())
                .fullName(account.getFullName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .companyName(account.getCompanyName())
                .roles(account.getRoles())
                .build();
    }

    public record LoginResult(boolean pendingOtp, LoginResponse response) {
    }
}
