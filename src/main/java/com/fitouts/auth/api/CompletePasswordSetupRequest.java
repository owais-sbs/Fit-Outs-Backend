package com.fitouts.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompletePasswordSetupRequest {

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;
}
