package com.fitouts.workitemconfiguration.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fitouts.shared.api.BaseController;
import com.fitouts.workitemconfiguration.application.WorkItemService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/work-items")
@Validated
@RequiredArgsConstructor
public class WorkItemController extends BaseController {

    private final WorkItemService workItemService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody WorkItemCreateRequest request) {
        try {
            return successResponse("Work item created successfully", workItemService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create work item", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @Valid @RequestBody WorkItemUpdateRequest request) {
        try {
            return successResponse("Work item updated successfully", workItemService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update work item", e.getMessage());
        }
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<?> clone(@PathVariable UUID id) {
        try {
            return successResponse("Work item cloned successfully", workItemService.clone(id));
        } catch (Exception e) {
            return failureResponse("Failed to clone work item", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return successResponse(workItemService.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch work item", e.getMessage());
        }
    }

    @PostMapping("/filter")
    public ResponseEntity<?> list(@RequestBody(required = false) WorkItemFilterRequest filter,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        try {
            Page<WorkItemResponse> result = workItemService.list(filter, page, size);
            return successResponse(result);
        } catch (Exception e) {
            return failureResponse("Failed to fetch work items", e.getMessage());
        }
    }

    @GetMapping("/by-surface/{surfaceType}")
    public ResponseEntity<?> listBySurfaceType(@PathVariable String surfaceType,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        try {
            Page<WorkItemResponse> result = workItemService.listBySurfaceType(surfaceType, page, size);
            return successResponse(result);
        } catch (Exception e) {
            return failureResponse("Failed to fetch work items by surface type", e.getMessage());
        }
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        try {
            return successResponse("Work item activated", workItemService.activate(id));
        } catch (Exception e) {
            return failureResponse("Failed to activate work item", e.getMessage());
        }
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        try {
            return successResponse("Work item deactivated", workItemService.deactivate(id));
        } catch (Exception e) {
            return failureResponse("Failed to deactivate work item", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> softDelete(@PathVariable UUID id) {
        try {
            workItemService.softDelete(id);
            return successResponse("Work item deleted successfully", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete work item", e.getMessage());
        }
    }
}
