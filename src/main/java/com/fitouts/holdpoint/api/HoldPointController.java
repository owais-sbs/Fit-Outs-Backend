package com.fitouts.holdpoint.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.holdpoint.application.HoldPointService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HoldPointController extends BaseController {

    private final HoldPointService holdPointService;

    @GetMapping("/api/projects/{projectId}/hold-points")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(holdPointService.list(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list hold points", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/hold-points/{uuid}")
    public Object get(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(holdPointService.get(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to load hold point", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/hold-points")
    public Object create(@PathVariable Long projectId, @RequestBody HoldPointRequest request) {
        try {
            return successResponse(holdPointService.create(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create hold point", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/hold-points/{uuid}")
    public Object update(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody HoldPointRequest request) {
        try {
            return successResponse(holdPointService.update(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update hold point", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/hold-points/{uuid}")
    public Object delete(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            holdPointService.delete(projectId, uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete hold point", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/hold-points/{uuid}/clear")
    public Object clear(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(holdPointService.clear(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to clear hold point", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/hold-points/{uuid}/hold")
    public Object hold(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(holdPointService.hold(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to hold hold point", e.getMessage());
        }
    }
}
