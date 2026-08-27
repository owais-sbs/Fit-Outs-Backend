package com.fitouts.planning.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.planning.application.PlanningService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/company/planning-gates")
@RequiredArgsConstructor
public class PlanningGateController extends BaseController {

    private final PlanningService planningService;

    @GetMapping
    public Object get() {
        try {
            return successResponse(planningService.getGateConfig());
        } catch (Exception e) {
            return failureResponse("Failed to load planning gates", e.getMessage());
        }
    }

    @PutMapping
    public Object update(@RequestBody PlanningGateConfigRequest request) {
        try {
            return successResponse(planningService.updateGateConfig(request));
        } catch (Exception e) {
            return failureResponse("Failed to update planning gates", e.getMessage());
        }
    }
}
