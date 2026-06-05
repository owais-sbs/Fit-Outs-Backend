package com.fitouts.tenant.api;

import java.util.UUID;

import com.fitouts.tenant.domain.TenantStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantUpdateRequest {

    @NotBlank
    private String companyName;

    private String logo;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Domain slug must use lowercase letters, numbers, and hyphens")
    @Schema(example = "string", description = "Lowercase tenant slug used in tenant URLs or domains")
    private String domainSlug;

    @NotNull
    private UUID subscriptionPlanUuid;

    @NotNull
    private TenantStatus status;
}
