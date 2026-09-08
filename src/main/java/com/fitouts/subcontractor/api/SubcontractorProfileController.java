package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.SubcontractorProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SubcontractorProfileController extends BaseController {

    private final SubcontractorProfileService profileService;

    @PostMapping("/api/v1/subcontractors/invite")
    public Object inviteSubcontractor(@RequestBody SubcontractorInviteRequest request) {
        try {
            return profileService.inviteSubcontractor(request);
        } catch (Exception e) {
            return failureResponse("Failed to invite subcontractor", e.getMessage());
        }
    }

    @PostMapping("/api/v1/subcontractors/register/verify-token")
    public Object verifyToken(@RequestBody SubcontractorTokenVerifyRequest request) {
        try {
            return successResponse(profileService.verifyToken(request != null ? request.getToken() : null));
        } catch (Exception e) {
            return failureResponse("Failed to verify token", e.getMessage());
        }
    }

    @PostMapping("/api/v1/subcontractors/register")
    public Object register(@RequestBody SubcontractorRegistrationRequest request) {
        try {
            return successResponse(profileService.register(request));
        } catch (Exception e) {
            return failureResponse("Failed to register subcontractor", e.getMessage());
        }
    }

    @GetMapping("/api/v1/subcontractors/{id}")
    public Object getProfile(@PathVariable UUID id) {
        try {
            return successResponse(profileService.getProfile(id));
        } catch (Exception e) {
            return failureResponse("Failed to load subcontractor profile", e.getMessage());
        }
    }

    @PutMapping("/api/v1/subcontractors/{id}/status")
    public Object updateStatus(
            @PathVariable UUID id,
            @RequestBody SubcontractorStatusUpdateRequest request) {
        try {
            return successResponse(profileService.updateStatus(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update status", e.getMessage());
        }
    }

    @PostMapping(value = "/api/v1/subcontractors/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object addDocument(
            @PathVariable UUID id,
            @RequestPart(value = "request", required = false) SubcontractorDocumentRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            return successResponse(profileService.addDocument(id, request, file));
        } catch (Exception e) {
            return failureResponse("Failed to add document", e.getMessage());
        }
    }

    @GetMapping("/api/v1/subcontractors/{id}/documents")
    public Object listDocuments(@PathVariable UUID id) {
        try {
            return successResponse(profileService.listDocuments(id));
        } catch (Exception e) {
            return failureResponse("Failed to list documents", e.getMessage());
        }
    }

    @PostMapping("/api/v1/subcontractors/{id}/users")
    public Object addTeamUser(
            @PathVariable UUID id,
            @RequestBody SubcontractorUserRequest request) {
        try {
            return successResponse(profileService.addTeamUser(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to add team user", e.getMessage());
        }
    }

    @GetMapping("/api/v1/subcontractors/{id}/users")
    public Object listTeamUsers(@PathVariable UUID id) {
        try {
            return successResponse(profileService.listTeamUsers(id));
        } catch (Exception e) {
            return failureResponse("Failed to list team users", e.getMessage());
        }
    }

    @PostMapping("/api/v1/subcontractors/{id}/workers")
    public Object addWorker(
            @PathVariable UUID id,
            @RequestBody SubcontractorWorkerRequest request) {
        try {
            return successResponse(profileService.addWorker(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to add worker", e.getMessage());
        }
    }

    @GetMapping("/api/v1/subcontractors/{id}/workers")
    public Object listWorkers(@PathVariable UUID id) {
        try {
            return successResponse(profileService.listWorkers(id));
        } catch (Exception e) {
            return failureResponse("Failed to list workers", e.getMessage());
        }
    }

    @GetMapping("/api/v1/subcontractors/{id}/eligibility")
    public Object evaluateEligibility(
            @PathVariable UUID id,
            @RequestParam(value = "project_id", required = false) Long projectId,
            @RequestParam(value = "package_id", required = false) UUID packageId) {
        try {
            return successResponse(profileService.evaluateEligibility(id));
        } catch (Exception e) {
            return failureResponse("Failed to evaluate eligibility", e.getMessage());
        }
    }
}
