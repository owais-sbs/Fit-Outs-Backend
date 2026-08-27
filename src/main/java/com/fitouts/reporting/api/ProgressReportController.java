package com.fitouts.reporting.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.reporting.application.ProgressReportService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProgressReportController extends BaseController {

    private final ProgressReportService progressReportService;

    @GetMapping("/api/projects/{projectId}/progress-report")
    public Object getReport(@PathVariable Long projectId) {
        try {
            return successResponse(progressReportService.getReport(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load progress report", e.getMessage());
        }
    }
}
