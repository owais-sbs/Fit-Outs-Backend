package com.fitouts.subcontractor.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScSignContractRequest {

    @NotBlank(message = "signatureName is required")
    private String signatureName;

    private String signerTitle;

    @AssertTrue(message = "You must accept the declaration to sign the contract")
    private boolean declarationAccepted;
}
