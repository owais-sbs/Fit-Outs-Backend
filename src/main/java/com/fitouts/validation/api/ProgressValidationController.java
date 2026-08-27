package com.fitouts.validation.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.validation.application.ProgressValidationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProgressValidationController extends BaseController {

    private final ProgressValidationService progressValidationService;

    @GetMapping("/api/validation/inbox")
    public Object inbox() {
        try {
            return successResponse(progressValidationService.inbox());
        } catch (Exception e) {
            return failureResponse("Failed to load validation inbox", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/validations")
    public Object listByProject(@PathVariable Long projectId) {
        try {
            return successResponse(progressValidationService.listByProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list validations", e.getMessage());
        }
    }

    @PostMapping("/api/validation/{uuid}/approve")
    public Object approve(@PathVariable UUID uuid) {
        try {
            return successResponse(progressValidationService.approve(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve validation", e.getMessage());
        }
    }

    @PostMapping("/api/validation/{uuid}/reject")
    public Object reject(@PathVariable UUID uuid, @RequestBody(required = false) ValidationRejectRequest request) {
        try {
            return successResponse(progressValidationService.reject(uuid,
                    request != null ? request : new ValidationRejectRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reject validation", e.getMessage());
        }
    }

    @PostMapping("/api/schedule/activities/{activityUuid}/progress/{progressUuid}/submit-for-validation")
    public Object submitForValidation(@PathVariable UUID activityUuid, @PathVariable UUID progressUuid) {
        try {
            return successResponse(progressValidationService.submitForValidation(activityUuid, progressUuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit for validation", e.getMessage());
        }
    }
}
