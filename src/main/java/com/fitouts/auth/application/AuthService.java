package com.fitouts.auth.application;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.api.AuthSessionResponse;
import com.fitouts.auth.api.ChangePasswordRequest;
import com.fitouts.auth.api.CurrentUserResponse;
import com.fitouts.auth.api.LoginRequest;
import com.fitouts.auth.api.LoginResponse;
import com.fitouts.auth.api.SignupRequest;
import com.fitouts.auth.api.VerifyOtpRequest;
import com.fitouts.auth.config.AuthProperties;
import com.fitouts.auth.domain.AccessPhase;
import com.fitouts.auth.domain.OtpChallenge;
import com.fitouts.auth.domain.RememberedDevice;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.checklist.mapper.SiteVisitEstimateMapper;
import com.fitouts.company.domain.Company;
import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.drawing.application.FileStorageService;
import com.fitouts.employee.domain.Feature;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.UnauthorizedException;
import com.fitouts.subscription.domain.SubscriptionPayment;
import com.fitouts.subscription.domain.SubscriptionPaymentRepository;
import com.fitouts.subscription.domain.SubscriptionPaymentStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final DeviceService deviceService;
    private final OtpService otpService;
    private final AuthProperties authProperties;
    private final SecurityContextRepository securityContextRepository;
    private final SessionTrackingService sessionTrackingService;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CookieService cookieService;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public LoginResult signup(
            SignupRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String fullName = request.getFullName().trim();
        if (fullName.isBlank()) {
            throw new BadRequestException("Full name is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }

        accountRepository.findByEmail(email).ifPresent(existing -> {
            throw new ConflictException("An account with this email already exists");
        });

        Account account = new Account();
        account.setFullName(fullName);
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setIsActive(true);
        account.setCompany(null);
        account.setRoles(new HashSet<>(Set.of(Role.ADMIN)));
        account = accountRepository.save(account);

        RememberedDevice device = deviceService.resolveDevice(account, servletRequest, servletResponse);
        return new LoginResult(false, authenticate(account, device, servletRequest, servletResponse));
    }

    @Transactional
    public LoginResult login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        Account account = accountService.findOptionalByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (account.getDeletionScheduledAt() != null && account.getPurgeAt() != null) {
            throw new UnauthorizedException("Account is scheduled for deletion. Contact the platform administrator.");
        }
        if (!Boolean.TRUE.equals(account.getIsActive())) {
            throw new UnauthorizedException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        assertCompanyAccessAllowed(account);

        RememberedDevice device = deviceService.resolveDevice(account, servletRequest, servletResponse);
        return new LoginResult(false, authenticate(account, device, servletRequest, servletResponse));
    }

    @Transactional
    public LoginResponse verifyOtp(
            VerifyOtpRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        OtpChallenge challenge = otpService.verify(request.getChallengeId(), request.getOtp());
        if (challenge.getAccount().getDeletionScheduledAt() != null
                && challenge.getAccount().getPurgeAt() != null) {
            throw new UnauthorizedException("Account is scheduled for deletion. Contact the platform administrator.");
        }
        if (!Boolean.TRUE.equals(challenge.getAccount().getIsActive())) {
            throw new UnauthorizedException("Account is inactive");
        }
        assertCompanyAccessAllowed(challenge.getAccount());
        return authenticate(challenge.getAccount(), challenge.getDevice(), servletRequest, servletResponse);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(AuthPrincipal principal) {
        Account account = accountService.getAccountByEmail(principal.getEmail());
        assertCompanyAccessAllowed(account);
        return toCurrentUser(account, principal);
    }

    /**
     * Re-binds the security context after company linkage (e.g. I've paid).
     */
    @Transactional
    public void refreshSession(
            Account account,
            HttpServletRequest request,
            HttpServletResponse response) {
        RememberedDevice device = deviceService.resolveDevice(account, request, response);
        authenticate(account, device, request, response);
    }

    @Transactional
    public void changePassword(AuthPrincipal principal, ChangePasswordRequest request) {
        Account account = accountService.getAccountByEmail(principal.getEmail());
        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }
        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
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
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        request.getSession(true);
        securityContextRepository.saveContext(context, request, response);

        if (request.getSession(false) == null) {
            throw new IllegalStateException("Session was not created");
        }

        sessionTrackingService.registerSession(request.getSession(false).getId(), account, device);
        return LoginResponse.builder()
                .status("AUTHENTICATED")
                .message("Login successful")
                .user(toCurrentUser(account, null))
                .build();
    }

    private void assertCompanyAccessAllowed(Account account) {
        if (!AccessPhaseResolver.isLoginAllowed(account)) {
            throw new UnauthorizedException(
                    "Company subscription is inactive. Contact the platform administrator.");
        }
    }

    private CurrentUserResponse toCurrentUser(Account account, AuthPrincipal principal) {
        UUID companyId = principal != null && principal.getCompanyId() != null
                ? principal.getCompanyId()
                : (account.getCompany() != null ? account.getCompany().getUuid() : null);
        // Prefer live account company when session principal is stale after payment.
        if (account.getCompany() != null) {
            companyId = account.getCompany().getUuid();
        }
        String companyName = account.getCompany() != null && account.getCompany().getCompanyName() != null
                ? account.getCompany().getCompanyName()
                : account.getCompanyName();
        Set<Feature> enabledFeatures = account.getCompany() != null && account.getCompany().getEnabledFeatures() != null
                ? Set.copyOf(account.getCompany().getEnabledFeatures())
                : Set.of();

        AccessPhase accessPhase = AccessPhaseResolver.resolve(account);
        Company company = account.getCompany();
        CompanyStatus companyStatus = company != null ? company.getStatus() : null;
        Boolean onboardingCompleted = company != null ? Boolean.TRUE.equals(company.getOnboardingCompleted()) : false;
        SubscriptionPaymentStatus pendingPaymentStatus = null;
        if (company != null) {
            pendingPaymentStatus = subscriptionPaymentRepository
                    .findFirstByCompanyUuidAndStatusOrderByCreatedAtDesc(
                            company.getUuid(), SubscriptionPaymentStatus.PENDING)
                    .map(SubscriptionPayment::getStatus)
                    .orElse(null);
        }

        return CurrentUserResponse.builder()
                .id(account.getId())
                .companyId(companyId)
                .companyName(companyName)
                .companyLogo(resolveCompanyLogoUrl(company))
                .fullName(account.getFullName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .roles(account.getRoles())
                .enabledFeatures(enabledFeatures)
                .accessPhase(accessPhase)
                .companyStatus(companyStatus)
                .onboardingCompleted(onboardingCompleted)
                .pendingPaymentStatus(pendingPaymentStatus)
                .build();
    }

    /**
     * Prefer CloudFront/S3 public URL when configured; otherwise same-origin {@code /api/files/...}.
     */
    private String resolveCompanyLogoUrl(Company company) {
        if (company == null || company.getLogo() == null || company.getLogo().isBlank()) {
            return null;
        }
        String publicUrl = fileStorageService.publicUrl(company.getLogo());
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl;
        }
        return SiteVisitEstimateMapper.toFileUrl(company.getLogo());
    }

    public record LoginResult(boolean pendingOtp, LoginResponse response) {
    }
}
