package com.fitouts.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestPasswordSetupRequest {

    @NotBlank
    @Email
    private String email;
}
