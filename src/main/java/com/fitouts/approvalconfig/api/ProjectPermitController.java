package com.fitouts.approvalconfig.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.approvalconfig.application.ProjectPermitService;
import com.fitouts.project.application.ProjectService;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProjectPermitController extends BaseController {

    private final ProjectPermitService projectPermitService;
    private final ProjectService projectService;

    @GetMapping("/projects/{projectId}/permit-cases")
    public ResponseEntity<?> list(@PathVariable Long projectId) {
        try {
            var project = projectService.getById(projectId);
            return successResponse(projectPermitService.listForProject(project.getId(), project.getCompanyId()));
        } catch (Exception e) {
            return failureResponse("Failed to fetch permit cases", e.getMessage());
        }
    }

    @PatchMapping("/permit-cases/{caseId}")
    public ResponseEntity<?> updateCase(@PathVariable UUID caseId, @RequestBody PermitCaseStatusRequest request) {
        try {
            return successResponse("Permit case updated", projectPermitService.updateCase(caseId, request));
        } catch (Exception e) {
            return failureResponse("Failed to update permit case", e.getMessage());
        }
    }

    @PatchMapping("/permit-documents/{documentId}")
    public ResponseEntity<?> updateDocument(
            @PathVariable UUID documentId,
            @RequestBody PermitDocumentStatusRequest request) {
        try {
            projectPermitService.updateDocument(documentId, request);
            return successResponse("Permit document updated", null);
        } catch (Exception e) {
            return failureResponse("Failed to update permit document", e.getMessage());
        }
    }
}
