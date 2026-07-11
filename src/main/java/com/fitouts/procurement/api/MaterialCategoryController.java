package com.fitouts.procurement.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fitouts.shared.api.BaseController;
import com.fitouts.procurement.application.MaterialCategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/material-categories")
@RequiredArgsConstructor
public class MaterialCategoryController extends BaseController {

    private final MaterialCategoryService service;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody MaterialCategoryCreateRequest request) {
        try {
            return successResponse("Material category created", service.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create material category", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody MaterialCategoryUpdateRequest request) {
        try {
            return successResponse("Material category updated", service.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update material category", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return successResponse(service.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch material category", e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return successResponse(service.list());
        } catch (Exception e) {
            return failureResponse("Failed to list material categories", e.getMessage());
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
