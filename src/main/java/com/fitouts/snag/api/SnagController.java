package com.fitouts.snag.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.web.BaseController;
import com.fitouts.snag.application.SnagService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SnagController extends BaseController {

    private final SnagService snagService;

    @GetMapping("/api/projects/{projectId}/snags")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(snagService.list(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list snags", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/snags/{uuid}")
    public Object get(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(snagService.get(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to load snag", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/snags")
    public Object create(@PathVariable Long projectId, @RequestBody SnagRequest request) {
        try {
            return successResponse(snagService.create(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create snag", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/snags/{uuid}")
    public Object update(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody SnagRequest request) {
        try {
            return successResponse(snagService.update(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update snag", e.getMessage());
        }
    }

    @PatchMapping("/api/projects/{projectId}/snags/{uuid}/status")
    public Object patchStatus(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody SnagStatusRequest request) {
        try {
            return successResponse(snagService.patchStatus(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update snag status", e.getMessage());
        }
    }

    @PostMapping(value = {
            "/api/projects/{projectId}/snags/{uuid}/photos",
            "/api/projects/{projectId}/snags/{uuid}/photo"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadPhoto(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        try {
            MultipartFile upload = file != null && !file.isEmpty() ? file : photo;
            return successResponse(snagService.uploadPhoto(projectId, uuid, upload));
        } catch (Exception e) {
            return failureResponse("Failed to upload snag photo", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/snags/{uuid}")
    public Object delete(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            snagService.delete(projectId, uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete snag", e.getMessage());
        }
    }

    @GetMapping("/api/client/projects/{projectId}/snags")
    public Object listClientVisible(@PathVariable Long projectId) {
        try {
            return successResponse(snagService.listClientVisible(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list client snags", e.getMessage());
        }
    }

    @PostMapping("/api/client/projects/{projectId}/snags")
    public Object createByClient(@PathVariable Long projectId, @RequestBody SnagRequest request) {
        try {
            return successResponse(snagService.createByClient(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create client snag", e.getMessage());
        }
    }

    @PostMapping("/api/client/projects/{projectId}/snags/{uuid}/approve")
    public Object clientApprove(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(snagService.clientApprove(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve snag", e.getMessage());
        }
    }

    @PostMapping(value = {
            "/api/client/projects/{projectId}/snags/{uuid}/photos",
            "/api/client/projects/{projectId}/snags/{uuid}/photo"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object clientUploadPhoto(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        try {
            MultipartFile upload = file != null && !file.isEmpty() ? file : photo;
            return successResponse(snagService.uploadPhoto(projectId, uuid, upload));
        } catch (Exception e) {
            return failureResponse("Failed to upload snag photo", e.getMessage());
        }
    }
}
