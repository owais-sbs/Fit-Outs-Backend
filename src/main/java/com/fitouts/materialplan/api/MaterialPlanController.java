package com.fitouts.materialplan.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.materialplan.application.MaterialPlanService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/material-plan")
@RequiredArgsConstructor
public class MaterialPlanController extends BaseController {

    private final MaterialPlanService materialPlanService;

    @GetMapping
    public Object get(@PathVariable Long projectId) {
        try {
            return successResponse(materialPlanService.get(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load material plan", e.getMessage());
        }
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<?> exportCsv(@PathVariable Long projectId) {
        try {
            String csv = materialPlanService.exportCsv(projectId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"material-plan.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (Exception e) {
            return failureResponse("Failed to export material plan", e.getMessage());
        }
    }

    @PostMapping("/generate")
    public Object generate(@PathVariable Long projectId) {
        try {
            return successResponse(materialPlanService.generate(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to generate material plan", e.getMessage());
        }
    }

    @PutMapping
    public Object update(@PathVariable Long projectId, @RequestBody MaterialPlanUpdateRequest request) {
        try {
            return successResponse(materialPlanService.update(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to update material plan", e.getMessage());
        }
    }

    @PostMapping("/reserve")
    public Object reserve(@PathVariable Long projectId) {
        try {
            return successResponse(materialPlanService.reserve(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to reserve materials", e.getMessage());
        }
    }
}
