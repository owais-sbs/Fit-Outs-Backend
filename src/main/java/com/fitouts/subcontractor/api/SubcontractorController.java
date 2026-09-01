package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.SubcontractorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SubcontractorController extends BaseController {

    private final SubcontractorService subcontractorService;

    @GetMapping("/api/projects/{projectId}/sc-packages")
    public Object listPackages(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.listPackages(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list subcontractor packages", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-packages/{uuid}")
    public Object getPackage(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.getPackage(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to load subcontractor package", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-packages/generate-from-boq")
    public Object generateFromBoq(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.generateFromBoq(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to generate packages from BOQ", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-packages")
    public Object createPackage(@PathVariable Long projectId, @RequestBody SubcontractorPackageRequest request) {
        try {
            return successResponse(subcontractorService.createPackage(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create subcontractor package", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/sc-packages/{uuid}")
    public Object updatePackage(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody SubcontractorPackageRequest request) {
        try {
            return successResponse(subcontractorService.updatePackage(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update subcontractor package", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/sc-packages/{uuid}")
    public Object deletePackage(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            subcontractorService.deletePackage(projectId, uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete subcontractor package", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-packages/{uuid}/appoint")
    public Object appoint(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody AppointSubcontractorRequest request) {
        try {
            return successResponse(subcontractorService.appoint(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to appoint subcontractor", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/my-packages")
    public Object myPackages() {
        try {
            return successResponse(subcontractorService.myPackages());
        } catch (Exception e) {
            return failureResponse("Failed to load my packages", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/my-projects")
    public Object myProjects() {
        try {
            return successResponse(subcontractorService.myProjects());
        } catch (Exception e) {
            return failureResponse("Failed to load my projects", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/projects/{projectId}")
    public Object getMyProject(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.getMyProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load project", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/packages/{uuid}/claims")
    public Object createClaim(@PathVariable UUID uuid, @RequestBody SubcontractorClaimRequest request) {
        try {
            return successResponse(subcontractorService.createClaim(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to create claim", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/packages/{uuid}/claims")
    public Object listClaims(@PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.listClaimsForPackage(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to list claims", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-claims")
    public Object listProjectClaims(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.listClaimsForProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list project claims", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/claims/{uuid}/submit")
    public Object submitClaim(@PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.submitClaim(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit claim", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-claims/{uuid}/approve")
    public Object approveClaim(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.approveClaim(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve claim", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-claims/{uuid}/reject")
    public Object rejectClaim(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) ClaimRejectRequest request) {
        try {
            return successResponse(subcontractorService.rejectClaim(projectId, uuid,
                    request != null ? request : new ClaimRejectRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reject claim", e.getMessage());
        }
    }
}
