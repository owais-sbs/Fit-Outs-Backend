package com.fitouts.auth.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String status;
    private String message;
    private CurrentUserResponse user;
    private String challengeId;
    private String otp;
}
