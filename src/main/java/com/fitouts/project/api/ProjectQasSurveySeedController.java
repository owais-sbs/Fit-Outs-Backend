package com.fitouts.project.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.project.application.ProjectQasSurveySeedService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProjectQasSurveySeedController extends BaseController {

    private final ProjectQasSurveySeedService seedService;

    @GetMapping("/api/projects/{projectId}/qas-survey-seed")
    public Object get(@PathVariable Long projectId) {
        return successResponse(seedService.getForProject(projectId));
    }
}
