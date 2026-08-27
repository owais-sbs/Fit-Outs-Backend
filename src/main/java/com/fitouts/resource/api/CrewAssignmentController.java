package com.fitouts.resource.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.resource.application.CrewAssignmentService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CrewAssignmentController extends BaseController {

    private final CrewAssignmentService crewAssignmentService;

    @GetMapping("/api/projects/{projectId}/crew-assignments")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(crewAssignmentService.list(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list crew assignments", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/crew-assignments")
    public Object create(@PathVariable Long projectId, @RequestBody CrewAssignmentRequest request) {
        try {
            return successResponse(crewAssignmentService.create(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create crew assignment", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/crew-assignments/{assignmentUuid}")
    public Object delete(@PathVariable Long projectId, @PathVariable UUID assignmentUuid) {
        try {
            crewAssignmentService.delete(projectId, assignmentUuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete crew assignment", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/resource-utilisation")
    public Object utilisation(@PathVariable Long projectId) {
        try {
            return successResponse(crewAssignmentService.utilisation(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load resource utilisation", e.getMessage());
        }
    }
}
