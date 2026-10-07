package com.fitouts.reporting.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.reporting.application.FinalProjectReportService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FinalProjectReportController extends BaseController {

    private final FinalProjectReportService finalProjectReportService;

    /**
     * Live project report for the Final PDF. Scoped to {@code projectId}
     * through the existing project access check and each domain service.
     * The room-approval endpoint {@code /final-report} is unchanged.
     */
    @GetMapping("/api/projects/{projectId}/final-project-report")
    public Object get(@PathVariable Long projectId) {
        try {
            return successResponse(finalProjectReportService.build(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to build final project report", e.getMessage());
        }
    }
}
