package com.fitouts.subscription.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.api.BaseController;
import com.fitouts.subscription.application.SubscriptionPlanService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/subscription-plans")
@RequiredArgsConstructor
public class PublicSubscriptionPlanController extends BaseController {

    private final SubscriptionPlanService service;

    @GetMapping
    public ResponseEntity<?> getActivePlans() {
        try {
            List<SubscriptionPlanResponse> plans = service.getActivePlans();
            return successResponse(plans);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch subscription plans", exception.getMessage());
        }
    }
}
