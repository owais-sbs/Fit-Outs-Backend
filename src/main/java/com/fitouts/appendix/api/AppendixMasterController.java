package com.fitouts.appendix.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.appendix.application.AppendixMasterService;
import com.fitouts.appendix.dto.AppendixMasterRequest;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/appendix-masters")
@RequiredArgsConstructor
public class AppendixMasterController extends BaseController {

    private final AppendixMasterService service;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "false") boolean all) {
        try {
            return successResponse(all ? service.listAll() : service.listActive());
        } catch (Exception e) {
            return failureResponse("Failed to fetch appendix masters", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        try {
            return successResponse(service.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch appendix master", e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam String title,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer sortOrder) {
        try {
            AppendixMasterRequest request = new AppendixMasterRequest();
            request.setTitle(title);
            request.setDescription(description);
            request.setCategory(category);
            request.setSortOrder(sortOrder);
            return successResponse("Appendix master created", service.create(request, file));
        } catch (Exception e) {
            return failureResponse("Failed to create appendix master", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable UUID id,
            @RequestParam(required = false) String title,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) Boolean active) {
        try {
            AppendixMasterRequest request = new AppendixMasterRequest();
            request.setTitle(title);
            request.setDescription(description);
            request.setCategory(category);
            request.setSortOrder(sortOrder);
            request.setActive(active);
            return successResponse("Appendix master updated", service.update(id, request, file));
        } catch (Exception e) {
            return failureResponse("Failed to update appendix master", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            service.delete(id);
            return successResponse("Appendix master deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete appendix master", e.getMessage());
        }
    }
}
