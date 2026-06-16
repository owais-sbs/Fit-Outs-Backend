package com.fitouts.workitemconfiguration.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
// import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fitouts.shared.api.BaseController;
import com.fitouts.workitemconfiguration.application.WorkItemMasterService;

// import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/work-item-masters")
// @Validated
@RequiredArgsConstructor
public class WorkItemMasterController extends BaseController {

    private final WorkItemMasterService workItemMasterService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody WorkItemMasterCreateRequest request) {
        try {
            return successResponse("Work item master created successfully", workItemMasterService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create work item master", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody WorkItemMasterUpdateRequest request) {
        try {
            return successResponse("Work item master updated successfully", workItemMasterService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update work item master", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return successResponse(workItemMasterService.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch work item master", e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return successResponse(workItemMasterService.list());
        } catch (Exception e) {
            return failureResponse("Failed to fetch work item masters", e.getMessage());
        }
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        try {
            return successResponse("Work item master activated", workItemMasterService.activate(id));
        } catch (Exception e) {
            return failureResponse("Failed to activate work item master", e.getMessage());
        }
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        try {
            return successResponse("Work item master deactivated", workItemMasterService.deactivate(id));
        } catch (Exception e) {
            return failureResponse("Failed to deactivate work item master", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> softDelete(@PathVariable UUID id) {
        try {
            workItemMasterService.softDelete(id);
            return successResponse("Work item master deleted successfully", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete work item master", e.getMessage());
        }
    }
}
