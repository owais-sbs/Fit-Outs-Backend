package com.fitouts.procurement.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fitouts.shared.api.BaseController;
import com.fitouts.procurement.application.MaterialService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController extends BaseController {

    private final MaterialService service;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody MaterialCreateRequest request) {
        try {
            return successResponse("Material created", service.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create material", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody MaterialUpdateRequest request) {
        try {
            return successResponse("Material updated", service.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update material", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return successResponse(service.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch material", e.getMessage());
        }
    }

    @PostMapping("/filter")
    public ResponseEntity<?> filter(@RequestBody(required = false) MaterialFilterRequest filter,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        try {
            return successResponse(service.list(filter, page, size));
        } catch (Exception e) {
            return failureResponse("Failed to list materials", e.getMessage());
        }
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        try {
            return successResponse("Activated", service.activate(id));
        } catch (Exception e) {
            return failureResponse("Failed to activate", e.getMessage());
        }
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        try {
            return successResponse("Deactivated", service.deactivate(id));
        } catch (Exception e) {
            return failureResponse("Failed to deactivate", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            service.softDelete(id);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete", e.getMessage());
        }
    }
}
