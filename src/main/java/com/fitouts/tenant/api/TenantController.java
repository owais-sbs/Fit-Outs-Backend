package com.fitouts.tenant.api;

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

import com.fitouts.shared.api.BaseController;
import com.fitouts.shared.api.MessageResponse;
import com.fitouts.tenant.application.TenantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenants")
@Validated
@RequiredArgsConstructor
public class TenantController extends BaseController {

    private final TenantService service;

    @PostMapping("/AddTenant")
    public ResponseEntity<?> create(@Valid @RequestBody TenantCreateRequest request) {
        try {
            return successResponse("Tenant created successfully", service.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to create tenant", exception.getMessage());
        }
    }

    @GetMapping("/GetAllTenants")
    public ResponseEntity<?> getAll() {
        try {
            List<TenantResponse> tenants = service.getAll();
            return successResponse(tenants);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch tenants", exception.getMessage());
        }
    }

    @GetMapping("/GetTenantByUuid/{uuid}")
    public ResponseEntity<?> getByUuid(@PathVariable UUID uuid) {
        try {
            return successResponse(service.getByUuid(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch tenant", exception.getMessage());
        }
    }

    @PutMapping("/UpdateTenant/{uuid}")
    public ResponseEntity<?> update(@PathVariable UUID uuid, @Valid @RequestBody TenantUpdateRequest request) {
        try {
            return successResponse("Tenant updated successfully", service.update(uuid, request));
        } catch (Exception exception) {
            return failureResponse("Unable to update tenant", exception.getMessage());
        }
    }

    @PostMapping("/SuspendTenant/{uuid}")
    public ResponseEntity<?> suspend(@PathVariable UUID uuid) {
        try {
            return successResponse("Tenant suspended successfully", service.suspend(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to suspend tenant", exception.getMessage());
        }
    }

    @PostMapping("/TerminateTenant/{uuid}")
    public ResponseEntity<?> terminate(@PathVariable UUID uuid) {
        try {
            return successResponse("Tenant terminated successfully", service.terminate(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to terminate tenant", exception.getMessage());
        }
    }

    @DeleteMapping("/DeleteTenant/{uuid}")
    public ResponseEntity<?> delete(@PathVariable UUID uuid) {
        try {
            service.delete(uuid);
            return successResponse(new MessageResponse("Tenant deleted successfully"));
        } catch (Exception exception) {
            return failureResponse("Unable to delete tenant", exception.getMessage());
        }
    }
}
