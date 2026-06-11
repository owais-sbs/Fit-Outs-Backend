package com.fitouts.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.auth.config.AuthProperties;
import com.fitouts.auth.domain.RememberedDevice;
import com.fitouts.auth.domain.RememberedDeviceRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final RememberedDeviceRepository deviceRepository;
    private final CookieService cookieService;
    private final AuthProperties authProperties;

    @Transactional
    public RememberedDevice resolveDevice(
            Account account,
            HttpServletRequest request,
            HttpServletResponse response) {

        String rawToken = extractCookie(request, authProperties.getDeviceCookieName());
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");
        String ip = Optional.ofNullable(request.getRemoteAddr()).orElse("unknown");
        OffsetDateTime now = OffsetDateTime.now();

        RememberedDevice device = null;
        if (rawToken != null && !rawToken.isBlank()) {
            String tokenHash = sha256(rawToken);
            device = deviceRepository.findByTokenHash(tokenHash)
                    .filter(existing -> existing.getAccount().getId().equals(account.getId()))
                    .filter(existing -> !Boolean.TRUE.equals(existing.getRevoked()))
                    .orElse(null);
        }

        if (device == null) {
            rawToken = UUID.randomUUID().toString().replace("-", "");
            device = new RememberedDevice();
            device.setAccount(account);
            device.setCompany(account.getCompany());
            device.setTokenHash(sha256(rawToken));
            device.setFirstSeenAt(now);
        }

        device.setLabel(buildLabel(userAgent));
        device.setUserAgent(userAgent);
        device.setIpHash(sha256(ip));
        device.setLastSeenAt(now);
        device.setTrustedUntil(now.plusDays(authProperties.getDeviceCookieMaxAgeDays()));
        device.setRevoked(false);

        RememberedDevice saved = deviceRepository.save(device);
        cookieService.writeDeviceCookie(response, rawToken);
        return saved;
    }

    @Transactional
    public void touch(RememberedDevice device) {
        if (device == null) {
            return;
        }
        device.setLastSeenAt(OffsetDateTime.now());
        deviceRepository.save(device);
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String buildLabel(String userAgent) {
        String normalized = userAgent.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "Unknown device";
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
