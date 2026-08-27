package com.fitouts.holdpoint.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.holdpoint.application.HoldPointService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/company/quality-templates")
@RequiredArgsConstructor
public class QualityTemplateController extends BaseController {

    private final HoldPointService holdPointService;

    @GetMapping("/{activityType}")
    public Object get(@PathVariable String activityType) {
        try {
            return successResponse(holdPointService.getTemplate(activityType));
        } catch (Exception e) {
            return failureResponse("Failed to load quality template", e.getMessage());
        }
    }

    @PutMapping("/{activityType}")
    public Object put(@PathVariable String activityType, @RequestBody QualityTemplateRequest request) {
        try {
            return successResponse(holdPointService.putTemplate(activityType, request));
        } catch (Exception e) {
            return failureResponse("Failed to save quality template", e.getMessage());
        }
    }
}
