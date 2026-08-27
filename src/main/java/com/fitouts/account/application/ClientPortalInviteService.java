package com.fitouts.account.application;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.api.PasswordSetupTokenResponse;
import com.fitouts.auth.domain.PasswordSetupToken;
import com.fitouts.auth.domain.PasswordSetupTokenRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.shared.email.EmailMessage;
import com.fitouts.shared.email.EmailService;
import com.fitouts.shared.email.EmailTemplateService;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientPortalInviteService {

    private static final int TOKEN_VALID_DAYS = 7;

    private final PasswordSetupTokenRepository tokenRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;

    @Value("${app.public-url:http://localhost:3002}")
    private String publicUrl;

    @Transactional
    public boolean sendPortalInvite(Long accountId, String clientName) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (!StringUtils.hasText(account.getEmail())) {
            log.warn("Skipping client portal invite — account {} has no email", accountId);
            return false;
        }

        String tokenValue = issueToken(account);
        String setupUrl = buildSetupUrl(tokenValue);
        String greeting = StringUtils.hasText(clientName) ? clientName.trim() : account.getFullName();

        String html = emailTemplateService.render("client-portal-invite", Map.of(
                "clientName", StringUtils.hasText(greeting) ? greeting : "there",
                "setupUrl", setupUrl,
                "loginUrl", normalizePublicUrl() + "/login"));

        emailService.sendAsync(EmailMessage.builder()
                .to(account.getEmail())
                .subject("Welcome — set up your client portal access")
                .body(html)
                .html(true)
                .build());

        return true;
    }

    /**
     * Resend a client portal password-setup link. Always succeeds from the caller's
     * perspective when the email is well-formed; silently no-ops if no eligible account exists.
     */
    @Transactional
    public void resendSetupEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }

        Account account = accountRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (account == null) {
            log.debug("Password setup resend requested for unknown email");
            return;
        }

        if (!Boolean.TRUE.equals(account.getIsActive()) || !account.getRoles().contains(Role.CLIENT)) {
            log.debug("Password setup resend skipped for ineligible account {}", account.getId());
            return;
        }

        sendPortalInvite(account.getId(), account.getFullName());
    }

    @Transactional(readOnly = true)
    public PasswordSetupTokenResponse validateToken(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return invalid("Invalid or missing link.");
        }

        PasswordSetupToken token = tokenRepository.findByTokenAndConsumedAtIsNull(tokenValue.trim())
                .orElse(null);

        if (token == null) {
            return invalid("This link is invalid or has already been used.");
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return invalid("This link has expired. Please contact your project team for a new invite.");
        }

        Account account = token.getAccount();
        return PasswordSetupTokenResponse.builder()
                .valid(true)
                .email(account.getEmail())
                .fullName(account.getFullName())
                .message("Set a password to access your client portal.")
                .build();
    }

    @Transactional
    public void completePasswordSetup(String tokenValue, String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }

        PasswordSetupToken token = tokenRepository.findByTokenAndConsumedAtIsNull(tokenValue.trim())
                .orElseThrow(() -> new BadRequestException("This link is invalid or has already been used."));

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("This link has expired. Please contact your project team for a new invite.");
        }

        Account account = token.getAccount();
        account.setPassword(passwordEncoder.encode(password));
        account.setIsActive(true);
        accountRepository.save(account);

        token.setConsumedAt(OffsetDateTime.now());
        tokenRepository.save(token);
    }

    private String issueToken(Account account) {
        PasswordSetupToken token = new PasswordSetupToken();
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setAccount(account);
        token.setExpiresAt(OffsetDateTime.now().plus(TOKEN_VALID_DAYS, ChronoUnit.DAYS));
        token.setCreatedAt(OffsetDateTime.now());
        tokenRepository.save(token);
        return token.getToken();
    }

    private String buildSetupUrl(String tokenValue) {
        return normalizePublicUrl() + "/set-password?token=" + tokenValue;
    }

    private String normalizePublicUrl() {
        String url = publicUrl == null ? "http://localhost:3002" : publicUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private PasswordSetupTokenResponse invalid(String message) {
        return PasswordSetupTokenResponse.builder()
                .valid(false)
                .message(message)
                .build();
    }
}
