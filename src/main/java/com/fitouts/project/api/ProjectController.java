package com.fitouts.project.api;

import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.shared.web.BaseController;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController extends BaseController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public Object create(@RequestBody Project request) {
        try {
            return successResponse(projectService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create project", e.getMessage());
        }
    }

    @GetMapping
    public Object getAll() {
        try {
            return successResponse(projectService.getAll());
        } catch (Exception e) {
            return failureResponse("Failed to fetch projects", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Object getById(@PathVariable Long id) {
        try {
            return successResponse(projectService.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch project", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Project request) {
        try {
            return successResponse(projectService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update project", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {
        try {
            return successResponse(projectService.delete(id));
        } catch (Exception e) {
            return failureResponse("Failed to delete project", e.getMessage());
        }
    }
}
