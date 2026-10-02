package com.fitouts.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.PasswordResetToken;
import com.fitouts.auth.domain.PasswordResetTokenRepository;
import com.fitouts.shared.email.EmailMessage;
import com.fitouts.shared.email.EmailService;
import com.fitouts.shared.email.EmailTemplateService;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.GoneException;
import com.fitouts.shared.error.TooManyRequestsException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_VALID_MINUTES = 30;
    private static final long RATE_LIMIT_SECONDS = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokenRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;

    /** Rate-limit unknown emails so anti-enumeration does not become an open relay. */
    private final ConcurrentHashMap<String, Long> recentRequestsByEmail = new ConcurrentHashMap<>();

    @Value("${app.public-url:http://localhost:3000}")
    private String publicUrl;

    @Value("${app.login-url:https://fitouts.onepathsolutions.com}")
    private String loginUrl;

    /**
     * Always completes without revealing whether the account exists.
     * Throws {@link TooManyRequestsException} when rate-limited.
     */
    @Transactional
    public void requestReset(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }

        String normalized = email.trim().toLowerCase();
        enforceRateLimit(normalized);

        Account account = accountRepository.findByEmail(normalized).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getIsActive())) {
            log.debug("Password reset requested for unknown or inactive email");
            return;
        }

        if (isAccountRateLimited(account)) {
            throw new TooManyRequestsException("Too many requests, try again in a few minutes");
        }

        String rawToken = issueToken(account);
        sendResetEmail(account, rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (!StringUtils.hasText(rawToken)) {
            throw new GoneException("This password reset link is no longer valid.");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }

        String hash = hashToken(rawToken.trim());
        PasswordResetToken token = tokenRepository.findByTokenHashAndConsumedAtIsNull(hash)
                .orElseThrow(() -> new GoneException("This password reset link is no longer valid."));

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new GoneException("This password reset link is no longer valid.");
        }

        Account account = token.getAccount();
        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);

        token.setConsumedAt(OffsetDateTime.now());
        tokenRepository.save(token);
    }

    private void enforceRateLimit(String normalizedEmail) {
        long now = System.currentTimeMillis();
        Long last = recentRequestsByEmail.get(normalizedEmail);
        if (last != null && (now - last) < RATE_LIMIT_SECONDS * 1000L) {
            throw new TooManyRequestsException("Too many requests, try again in a few minutes");
        }
        recentRequestsByEmail.put(normalizedEmail, now);
        // Light cleanup to avoid unbounded growth
        if (recentRequestsByEmail.size() > 10_000) {
            recentRequestsByEmail.entrySet().removeIf(e -> (now - e.getValue()) > RATE_LIMIT_SECONDS * 1000L * 5);
        }
    }

    private boolean isAccountRateLimited(Account account) {
        return tokenRepository.findFirstByAccountAndConsumedAtIsNullOrderByCreatedAtDesc(account)
                .filter(t -> t.getCreatedAt().isAfter(OffsetDateTime.now().minus(RATE_LIMIT_SECONDS, ChronoUnit.SECONDS)))
                .isPresent();
    }

    private String issueToken(Account account) {
        tokenRepository.invalidateActiveForAccount(account, OffsetDateTime.now());

        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hashToken(rawToken));
        token.setAccount(account);
        token.setExpiresAt(OffsetDateTime.now().plus(TOKEN_VALID_MINUTES, ChronoUnit.MINUTES));
        token.setCreatedAt(OffsetDateTime.now());
        tokenRepository.save(token);

        return rawToken;
    }

    private void sendResetEmail(Account account, String rawToken) {
        String greeting = StringUtils.hasText(account.getFullName()) ? account.getFullName().trim() : "there";
        String resetUrl = normalizePublicUrl() + "/reset-password?token=" + rawToken;

        Map<String, Object> vars = Map.of(
                "clientName", greeting,
                "resetUrl", resetUrl,
                "loginUrl", normalizeLoginUrl() + "/login"
        );

        String html = emailTemplateService.render("password-reset", vars);
        EmailMessage message = EmailMessage.builder()
                .to(account.getEmail())
                .subject("Reset your password")
                .body(html)
                .html(true)
                .build();

        try {
            emailService.send(message);
            log.info("Password reset email sent for account {}", account.getId());
        } catch (Exception e) {
            log.warn("Password reset email failed for account {}: {}", account.getId(), e.getMessage());
        }
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String normalizePublicUrl() {
        return stripTrailingSlash(firstUrl(publicUrl), "http://localhost:3000");
    }

    private String normalizeLoginUrl() {
        return stripTrailingSlash(firstUrl(loginUrl), "https://fitouts.onepathsolutions.com");
    }

    /** Email links need a single origin; ignore accidental comma-lists (CORS-style). */
    private static String firstUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String first = value.split(",")[0].trim();
        return first;
    }

    private static String stripTrailingSlash(String value, String fallback) {
        String url = !StringUtils.hasText(value) ? fallback : value.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
