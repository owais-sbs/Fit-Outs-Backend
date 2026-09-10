package com.fitouts.subcontractor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScSubmittalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subcontractor/submittals")
@RequiredArgsConstructor
public class ScSubmittalController extends BaseController {

    private final ScSubmittalService submittalService;

    @GetMapping
    public Object listSubmittals() {
        try {
            return successResponse(submittalService.listMySubmittals());
        } catch (Exception e) {
            return failureResponse("Failed to load submittals", e.getMessage());
        }
    }

    @PostMapping
    public Object createSubmittal(@RequestBody ScSubmittalRequest request) {
        try {
            return successResponse(submittalService.createSubmittal(request));
        } catch (Exception e) {
            return failureResponse("Failed to create submittal", e.getMessage());
        }
    }
}
