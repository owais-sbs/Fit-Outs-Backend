package com.fitouts.projectdoc.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.projectdoc.application.ProjectDocumentService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProjectDocumentController extends BaseController {

    private final ProjectDocumentService projectDocumentService;

    @GetMapping("/api/projects/{projectId}/documents")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(projectDocumentService.list(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list documents", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/documents/{uuid}")
    public Object get(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(projectDocumentService.get(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to load document", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/documents")
    public Object create(@PathVariable Long projectId, @RequestBody ProjectDocumentRequest request) {
        try {
            return successResponse(projectDocumentService.create(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create document", e.getMessage());
        }
    }

    @PostMapping(value = "/api/projects/{projectId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(
            @PathVariable Long projectId,
            @RequestParam("title") String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentDocumentUuid", required = false) UUID parentDocumentUuid) {
        try {
            return successResponse(projectDocumentService.upload(projectId, title, category, file, parentDocumentUuid));
        } catch (Exception e) {
            return failureResponse("Failed to upload document", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/documents/{uuid}")
    public Object update(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody ProjectDocumentRequest request) {
        try {
            return successResponse(projectDocumentService.update(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update document", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/documents/{uuid}")
    public Object delete(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            projectDocumentService.delete(projectId, uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete document", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/documents/{uuid}/publish-to-client")
    public Object publishToClient(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(projectDocumentService.publishToClient(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to publish document", e.getMessage());
        }
    }

    @GetMapping("/api/client/projects/{projectId}/documents")
    public Object listPublished(@PathVariable Long projectId) {
        try {
            return successResponse(projectDocumentService.listPublished(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list published documents", e.getMessage());
        }
    }
}
