package com.fitouts.resource.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.resource.application.ActivityResourceAssignmentService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ActivityResourceAssignmentController extends BaseController {

    private final ActivityResourceAssignmentService assignmentService;

    @GetMapping("/api/projects/{projectId}/resource-assignments")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(assignmentService.list(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list resource assignments", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/resource-assignments")
    public Object create(@PathVariable Long projectId, @RequestBody ResourceAssignmentRequest request) {
        try {
            return successResponse(assignmentService.create(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create resource assignment", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/resource-assignments/{assignmentUuid}")
    public Object delete(@PathVariable Long projectId, @PathVariable UUID assignmentUuid) {
        try {
            assignmentService.delete(projectId, assignmentUuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete resource assignment", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/plant-tool-utilisation")
    public Object utilisation(@PathVariable Long projectId) {
        try {
            return successResponse(assignmentService.utilisation(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load plant/tool utilisation", e.getMessage());
        }
    }
}
