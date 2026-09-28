package com.fitouts.company.api;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.fitouts.company.domain.CompanyStatus;
import com.fitouts.employee.domain.Feature;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    @Size(max = 100)
    @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Domain slug must use lowercase letters, numbers, and hyphens")
    @Schema(example = "my-company", description = "Lowercase company slug used in company URLs or domains. Auto-generated from name when omitted.")
    private String domainSlug;

    private UUID subscriptionPlanUuid;

    private CompanyStatus status;

    @Email
    private String adminEmail;

    private String adminFullName;

    private String adminPassword;

    private Set<Feature> enabledFeatures = new HashSet<>();
}
