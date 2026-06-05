package com.fitouts.checklist.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.checklist.dto.ChecklistTemplateRequest;
import com.fitouts.checklist.dto.ChecklistTemplateResponse;
import com.fitouts.checklist.service.ChecklistTemplateService;
import com.fitouts.shared.api.BaseController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/checklist-templates")
@Validated
@RequiredArgsConstructor
public class ChecklistTemplateController extends BaseController {

    private final ChecklistTemplateService service;

    @PostMapping("/CreateCheckList")
    public ResponseEntity<?> create(@Valid @RequestBody ChecklistTemplateRequest request) {
        try {
            return successResponse("Checklist template created successfully", service.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to create checklist template", exception.getMessage());
        }
    }

    @GetMapping("/GetAllCheckList")
    public ResponseEntity<?> getAll() {
        try {
            List<ChecklistTemplateResponse> templates = service.getAll();
            return successResponse(templates);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch checklist templates", exception.getMessage());
        }
    }

    @GetMapping("/GetCheckListByUuid/{uuid}")
    public ResponseEntity<?> getByUuid(@PathVariable UUID uuid) {
        try {
            return successResponse(service.getByUuid(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch checklist template", exception.getMessage());
        }
    }
}
