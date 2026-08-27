package com.fitouts.resource.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.resource.application.ResourceTypeService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resource-types")
@RequiredArgsConstructor
public class ResourceTypeController extends BaseController {

    private final ResourceTypeService resourceTypeService;

    @GetMapping
    public Object list() {
        try {
            return successResponse(resourceTypeService.list());
        } catch (Exception e) {
            return failureResponse("Failed to list resource types", e.getMessage());
        }
    }

    @PostMapping
    public Object create(@RequestBody ResourceTypeRequest request) {
        try {
            return successResponse(resourceTypeService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create resource type", e.getMessage());
        }
    }

    @PutMapping("/{uuid}")
    public Object update(@PathVariable UUID uuid, @RequestBody ResourceTypeRequest request) {
        try {
            return successResponse(resourceTypeService.update(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update resource type", e.getMessage());
        }
    }

    @DeleteMapping("/{uuid}")
    public Object delete(@PathVariable UUID uuid) {
        try {
            resourceTypeService.delete(uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete resource type", e.getMessage());
        }
    }
}
