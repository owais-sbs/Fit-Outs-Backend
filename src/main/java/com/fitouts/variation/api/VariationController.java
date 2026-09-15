package com.fitouts.variation.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.web.BaseController;
import com.fitouts.variation.application.VariationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class VariationController extends BaseController {

    private final VariationService variationService;

    @GetMapping("/api/projects/{projectId}/variations")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(variationService.listForProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list variations", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/variations/{uuid}")
    public Object get(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(variationService.get(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to load variation", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/variations")
    public Object create(@PathVariable Long projectId, @RequestBody VariationUpsertRequest request) {
        try {
            return successResponse(variationService.create(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create variation", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/variations/{uuid}")
    public Object update(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody VariationUpsertRequest request) {
        try {
            return successResponse(variationService.update(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update variation", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/variations/{uuid}/triage")
    public Object triage(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody VariationTriageRequest request) {
        try {
            return successResponse(variationService.triage(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to triage variation", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/variations/{uuid}/submit-review")
    public Object submitReview(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(variationService.submitReview(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit for review", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/variations/{uuid}/approve")
    public Object clientApprove(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(variationService.clientApprove(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve variation", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/variations/{uuid}/reject")
    public Object clientReject(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody VariationDecisionRequest request) {
        try {
            return successResponse(variationService.clientReject(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to reject variation", e.getMessage());
        }
    }

    @PostMapping(value = "/api/projects/{projectId}/variations/{uuid}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestParam("file") MultipartFile file) {
        try {
            return successResponse(variationService.uploadAttachment(projectId, uuid, file));
        } catch (Exception e) {
            return failureResponse("Failed to upload attachment", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/commercial")
    public Object commercial(@PathVariable Long projectId) {
        try {
            return successResponse(variationService.getCommercial(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load commercial", e.getMessage());
        }
    }

    @GetMapping("/api/variations/triage-inbox")
    public Object triageInbox() {
        try {
            return successResponse(variationService.triageInbox());
        } catch (Exception e) {
            return failureResponse("Failed to load triage inbox", e.getMessage());
        }
    }
}
