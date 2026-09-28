package com.fitouts.company.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.company.application.CompanyService;
import com.fitouts.employee.domain.Feature;
import com.fitouts.shared.api.BaseController;
import com.fitouts.shared.api.MessageResponse;
import com.fitouts.shared.error.BadRequestException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/companies")
@Validated
@RequiredArgsConstructor
public class CompanyController extends BaseController {

    private final CompanyService service;
    private final ObjectMapper objectMapper;

    @PostMapping("/AddCompany")
    public ResponseEntity<?> create(@Valid @RequestBody CompanyCreateRequest request) {
        try {
            return successResponse("Company created successfully", service.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to create company", exception.getMessage());
        }
    }

    @PostMapping(value = "/provision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> provision(
            @RequestPart("companyName") String companyName,
            @RequestPart("adminEmail") String adminEmail,
            @RequestPart(value = "adminFullName", required = false) String adminFullName,
            @RequestPart(value = "domainSlug", required = false) String domainSlug,
            @RequestPart(value = "subscriptionPlanUuid", required = false) String subscriptionPlanUuid,
            @RequestPart(value = "enabledFeatures", required = false) String enabledFeaturesJson,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "stamp", required = false) MultipartFile stamp,
            @RequestPart(value = "signature", required = false) MultipartFile signature) {
        try {
            CompanyCreateRequest request = new CompanyCreateRequest();
            request.setCompanyName(companyName);
            request.setAdminEmail(adminEmail);
            request.setAdminFullName(adminFullName);
            request.setDomainSlug(domainSlug);
            if (subscriptionPlanUuid != null && !subscriptionPlanUuid.isBlank()) {
                request.setSubscriptionPlanUuid(UUID.fromString(subscriptionPlanUuid.trim()));
            }
            request.setEnabledFeatures(parseEnabledFeatures(enabledFeaturesJson));
            return successResponse(
                    "Company provisioned successfully",
                    service.provision(request, logo, stamp, signature));
        } catch (Exception exception) {
            return failureResponse("Unable to provision company", exception.getMessage());
        }
    }

    @GetMapping("/GetAllCompanies")
    public ResponseEntity<?> getAll() {
        try {
            List<CompanyResponse> companies = service.getAll();
            return successResponse(companies);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch companies", exception.getMessage());
        }
    }

    @GetMapping("/GetCompanyByUuid/{uuid}")
    public ResponseEntity<?> getByUuid(@PathVariable UUID uuid) {
        try {
            return successResponse(service.getByUuid(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch company", exception.getMessage());
        }
    }

    @PutMapping("/UpdateCompany/{uuid}")
    public ResponseEntity<?> update(@PathVariable UUID uuid, @Valid @RequestBody CompanyUpdateRequest request) {
        try {
            return successResponse("Company updated successfully", service.update(uuid, request));
        } catch (Exception exception) {
            return failureResponse("Unable to update company", exception.getMessage());
        }
    }

    @PostMapping("/ActivateCompany/{uuid}")
    public ResponseEntity<?> activate(@PathVariable UUID uuid) {
        try {
            return successResponse("Company activated successfully", service.activate(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to activate company", exception.getMessage());
        }
    }

    @PostMapping("/SuspendCompany/{uuid}")
    public ResponseEntity<?> suspend(@PathVariable UUID uuid) {
        try {
            return successResponse("Company suspended successfully", service.suspend(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to suspend company", exception.getMessage());
        }
    }

    @PostMapping("/TerminateCompany/{uuid}")
    public ResponseEntity<?> terminate(@PathVariable UUID uuid) {
        try {
            return successResponse("Company terminated successfully", service.terminate(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to terminate company", exception.getMessage());
        }
    }

    @DeleteMapping("/DeleteCompany/{uuid}")
    public ResponseEntity<?> delete(@PathVariable UUID uuid) {
        try {
            service.delete(uuid);
            return successResponse(new MessageResponse("Company deleted successfully"));
        } catch (Exception exception) {
            return failureResponse("Unable to delete company", exception.getMessage());
        }
    }

    private Set<Feature> parseEnabledFeatures(String enabledFeaturesJson) {
        if (enabledFeaturesJson == null || enabledFeaturesJson.isBlank()) {
            return new HashSet<>();
        }
        try {
            List<String> raw = objectMapper.readValue(enabledFeaturesJson, new TypeReference<>() {
            });
            Set<Feature> features = new HashSet<>();
            for (String value : raw) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                features.add(Feature.valueOf(value.trim().toUpperCase()));
            }
            return features;
        } catch (Exception exception) {
            throw new BadRequestException("Invalid enabledFeatures payload");
        }
    }
}
