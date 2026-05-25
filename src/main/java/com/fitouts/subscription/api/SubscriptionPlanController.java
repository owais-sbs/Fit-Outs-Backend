package com.fitouts.subscription.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.api.BaseController;
import com.fitouts.shared.api.MessageResponse;
import com.fitouts.subscription.application.SubscriptionPlanService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscription-plans")
@Validated
@RequiredArgsConstructor
public class SubscriptionPlanController extends BaseController {

    private final SubscriptionPlanService service;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SubscriptionPlanRequest request) {
        try {
            return successResponse("Subscription plan created successfully", service.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to create subscription plan", exception.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            List<SubscriptionPlanResponse> plans = service.getAll();
            return successResponse(plans);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch subscription plans", exception.getMessage());
        }
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<?> getByUuid(@PathVariable UUID uuid) {
        try {
            return successResponse(service.getByUuid(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch subscription plan", exception.getMessage());
        }
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<?> update(@PathVariable UUID uuid, @Valid @RequestBody SubscriptionPlanRequest request) {
        try {
            return successResponse("Subscription plan updated successfully", service.update(uuid, request));
        } catch (Exception exception) {
            return failureResponse("Unable to update subscription plan", exception.getMessage());
        }
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<?> deactivate(@PathVariable UUID uuid) {
        try {
            service.deactivate(uuid);
            return successResponse(new MessageResponse("Subscription plan deactivated successfully"));
        } catch (Exception exception) {
            return failureResponse("Unable to deactivate subscription plan", exception.getMessage());
        }
    }
}
