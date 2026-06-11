package com.fitouts.company.api;

import java.util.UUID;

import com.fitouts.company.domain.CompanyStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyCreateRequest {

    @NotBlank
    private String companyName;

    private String logo;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Domain slug must use lowercase letters, numbers, and hyphens")
    @Schema(example = "my-company", description = "Lowercase company slug used in company URLs or domains")
    private String domainSlug;

    @NotNull
    private UUID subscriptionPlanUuid;

    private CompanyStatus status;
}
