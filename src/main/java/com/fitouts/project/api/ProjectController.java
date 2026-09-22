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
            return failureResponse("Failed to create project", safeClientError(e));
        }
    }

    @GetMapping
    public Object getAll() {
        try {
            return successResponse(projectService.getAll());
        } catch (Exception e) {
            return failureResponse("Failed to fetch projects", safeClientError(e));
        }
    }

    @GetMapping("/{id}")
    public Object getById(@PathVariable Long id) {
        try {
            return successResponse(projectService.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch project", safeClientError(e));
        }
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Project request) {
        try {
            return successResponse(projectService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update project", safeClientError(e));
        }
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {
        try {
            return successResponse(projectService.delete(id));
        } catch (Exception e) {
            return failureResponse("Failed to delete project", safeClientError(e));
        }
    }

    private static String safeClientError(Exception e) {
        if (e instanceof com.fitouts.shared.error.ApiException api) {
            return api.getMessage();
        }
        String msg = e.getMessage();
        if (msg != null && (msg.contains("JDBC") || msg.contains("SQL")
                || msg.contains("does not exist") || msg.contains("PSQLException"))) {
            return "Unable to load projects right now. Please try again shortly.";
        }
        return msg != null && !msg.isBlank() ? msg : "Unexpected server error";
    }
}
