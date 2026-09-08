package com.fitouts.subcontractor.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubcontractorTokenVerifyRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
