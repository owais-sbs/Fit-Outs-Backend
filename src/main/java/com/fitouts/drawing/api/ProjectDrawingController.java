package com.fitouts.drawing.api;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.drawing.application.ProjectDrawingService;
import com.fitouts.shared.api.BaseController;
import com.fitouts.shared.enums.DrawingCategory;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/drawings")
@RequiredArgsConstructor
public class ProjectDrawingController extends BaseController {

    private final ProjectDrawingService drawingService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @PathVariable Long projectId,
            @RequestParam("category") DrawingCategory category,
            @RequestParam("file") MultipartFile file) {
        try {
            return successResponse("Drawing uploaded", drawingService.upload(projectId, category, file));
        } catch (Exception e) {
            return failureResponse("Failed to upload drawing", e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long projectId) {
        try {
            return successResponse(drawingService.listByProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list drawings", e.getMessage());
        }
    }

    @GetMapping("/{drawingId}")
    public ResponseEntity<?> get(@PathVariable Long projectId, @PathVariable UUID drawingId) {
        try {
            return successResponse(drawingService.getById(drawingId));
        } catch (Exception e) {
            return failureResponse("Failed to fetch drawing", e.getMessage());
        }
    }

    @GetMapping("/{drawingId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long projectId, @PathVariable UUID drawingId) {
        Resource resource = drawingService.getPreviewResource(drawingId);
        MediaType mediaType = drawingService.getPreviewMediaType(drawingId);
        String filename = mediaType.equals(MediaType.APPLICATION_PDF)
            ? "preview.pdf"
            : mediaType.equals(MediaType.IMAGE_PNG)
                ? "preview.png"
                : mediaType.getType().equals("application") && "dxf".equals(mediaType.getSubtype())
                    ? "preview.dxf"
                    : "preview.svg";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping("/{drawingId}/reconvert")
    public ResponseEntity<?> reconvert(@PathVariable Long projectId, @PathVariable UUID drawingId) {
        try {
            return successResponse("DWG conversion retried", drawingService.reconvert(drawingId));
        } catch (Exception e) {
            return failureResponse("Failed to convert DWG", e.getMessage());
        }
    }

    @DeleteMapping("/{drawingId}")
    public ResponseEntity<?> delete(@PathVariable Long projectId, @PathVariable UUID drawingId) {
        try {
            drawingService.delete(drawingId);
            return successResponse("Drawing deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete drawing", e.getMessage());
        }
    }
}
