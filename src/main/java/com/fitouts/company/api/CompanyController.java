package com.fitouts.company.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import com.fitouts.company.application.CompanyService;
import com.fitouts.shared.api.BaseController;
import com.fitouts.shared.api.MessageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/companies")
@Validated
@RequiredArgsConstructor
public class CompanyController extends BaseController {

    private final CompanyService service;

    @PostMapping("/AddCompany")
    public ResponseEntity<?> create(@Valid @RequestBody CompanyCreateRequest request) {
        try {
            return successResponse("Company created successfully", service.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to create company", exception.getMessage());
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
}
