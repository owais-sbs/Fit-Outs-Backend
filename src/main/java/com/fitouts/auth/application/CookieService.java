package com.fitouts.auth.application;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.fitouts.auth.config.AuthProperties;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CookieService {

    private final AuthProperties authProperties;

    public void writeDeviceCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = baseCookie(authProperties.getDeviceCookieName(), token)
                .maxAge(authProperties.getDeviceCookieMaxAgeDays() * 24L * 60L * 60L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(authProperties.getSessionCookieName(), "")
                .maxAge(0)
                .build()
                .toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .sameSite(authProperties.isCookieSecure() ? "None" : "Lax")
                .path("/");
    }
}
