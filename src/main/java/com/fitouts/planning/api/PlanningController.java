package com.fitouts.planning.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.planning.application.PlanningService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/planning")
@RequiredArgsConstructor
public class PlanningController extends BaseController {

    private final PlanningService planningService;

    @GetMapping
    public Object get(@PathVariable Long projectId) {
        try {
            return successResponse(planningService.get(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load planning status", e.getMessage());
        }
    }

    @PutMapping
    public Object update(@PathVariable Long projectId, @RequestBody PlanningStatusRequest request) {
        try {
            return successResponse(planningService.update(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to update planning status", e.getMessage());
        }
    }

    @GetMapping("/audit")
    public Object listAudit(@PathVariable Long projectId) {
        try {
            return successResponse(planningService.listAudit(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load planning audit", e.getMessage());
        }
    }
}
