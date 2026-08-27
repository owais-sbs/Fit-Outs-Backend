package com.fitouts.auth.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PasswordSetupTokenResponse {
    private String email;
    private String fullName;
    private boolean valid;
    private String message;
}
