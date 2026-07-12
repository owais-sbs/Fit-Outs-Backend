package com.fitouts.auth.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    private String sessionCookieName = "FITOUTS_SESSION";
    private boolean cookieSecure;
    private String cookieSameSite;
    private String deviceCookieName = "FITOUTS_DEVICE";
    private int deviceCookieMaxAgeDays = 30;
    private Otp otp = new Otp();

    public String getCookieSameSite() {
        if (cookieSameSite != null && !cookieSameSite.isBlank()) {
            return cookieSameSite;
        }
        return cookieSecure ? "None" : "Lax";
    }

    @Getter
    @Setter
    public static class Otp {
        private int expiryMinutes = 5;
        private int maxAttempts = 5;
        private boolean superAdminEnabled = true;
        private boolean devBypassEnabled;
        private boolean devExposeValue;
    }
}
