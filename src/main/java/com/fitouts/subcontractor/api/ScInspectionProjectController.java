package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScInspectionRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/sc-inspections")
@RequiredArgsConstructor
public class ScInspectionProjectController extends BaseController {

    private final ScInspectionRequestService inspectionService;

    @GetMapping
    public ResponseEntity<?> listProjectInspections(@PathVariable Long projectId) {
        try {
            return successResponse(inspectionService.listProjectInspections(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list project inspection requests", e.getMessage());
        }
    }

    @PostMapping("/{inspectionUuid}/review")
    public ResponseEntity<?> reviewInspection(
            @PathVariable Long projectId,
            @PathVariable UUID inspectionUuid,
            @Valid @RequestBody ScInspectionReviewRequest request) {
        try {
            return successResponse("Inspection request reviewed", inspectionService.reviewInspection(projectId, inspectionUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to review inspection request", e.getMessage());
        }
    }
}
