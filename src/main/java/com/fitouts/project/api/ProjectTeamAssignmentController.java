package com.fitouts.project.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.project.application.ProjectTeamAssignmentService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProjectTeamAssignmentController extends BaseController {

    private final ProjectTeamAssignmentService teamAssignmentService;

    @GetMapping("/api/projects/{projectId}/team-assignments")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(teamAssignmentService.list(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list project team assignments", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/team-assignments")
    public Object sync(@PathVariable Long projectId, @RequestBody ProjectTeamAssignmentSyncRequest request) {
        try {
            return successResponse(teamAssignmentService.sync(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to update project team assignments", e.getMessage());
        }
    }
}
