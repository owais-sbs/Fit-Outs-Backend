package com.fitouts.schedule.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.schedule.application.ScheduleDurationExtensionService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScheduleDurationExtensionController extends BaseController {

    private final ScheduleDurationExtensionService durationExtensionService;

    @PostMapping("/api/schedule/activities/{activityUuid}/duration-extensions")
    public Object create(
            @PathVariable UUID activityUuid,
            @RequestBody DurationExtensionCreateRequest request) {
        try {
            return successResponse(durationExtensionService.create(activityUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to request duration extension", e.getMessage());
        }
    }

    @GetMapping("/api/schedule/duration-extensions/inbox")
    public Object inbox() {
        try {
            return successResponse(durationExtensionService.inbox());
        } catch (Exception e) {
            return failureResponse("Failed to load duration extension inbox", e.getMessage());
        }
    }

    @PostMapping("/api/schedule/duration-extensions/{uuid}/approve")
    public Object approve(
            @PathVariable UUID uuid,
            @RequestBody(required = false) DurationExtensionDecisionRequest request) {
        try {
            return successResponse(durationExtensionService.approve(uuid,
                    request != null ? request : new DurationExtensionDecisionRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to approve duration extension", e.getMessage());
        }
    }

    @PostMapping("/api/schedule/duration-extensions/{uuid}/reject")
    public Object reject(
            @PathVariable UUID uuid,
            @RequestBody(required = false) DurationExtensionDecisionRequest request) {
        try {
            return successResponse(durationExtensionService.reject(uuid,
                    request != null ? request : new DurationExtensionDecisionRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reject duration extension", e.getMessage());
        }
    }
}
