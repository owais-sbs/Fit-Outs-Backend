package com.fitouts.subscription.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.api.BaseController;
import com.fitouts.subscription.application.SubscriptionPaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscription-payments")
@Validated
@RequiredArgsConstructor
public class SubscriptionPaymentController extends BaseController {

    private final SubscriptionPaymentService service;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal AuthPrincipal principal) {
        try {
            List<SubscriptionPaymentResponse> payments = service.listAll(principal);
            return successResponse(payments);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch subscription payments", exception.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody SubscriptionPaymentCreateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            return successResponse("Payment recorded successfully", service.create(request, principal));
        } catch (Exception exception) {
            return failureResponse("Unable to create payment", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/mark-paid")
    public ResponseEntity<?> markPaid(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            return successResponse("Payment marked as paid", service.markPaid(uuid, principal));
        } catch (Exception exception) {
            return failureResponse("Unable to mark payment as paid", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/mark-failed")
    public ResponseEntity<?> markFailed(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            return successResponse("Payment marked as failed", service.markFailed(uuid, principal));
        } catch (Exception exception) {
            return failureResponse("Unable to mark payment as failed", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            return successResponse("Payment cancelled", service.cancel(uuid, principal));
        } catch (Exception exception) {
            return failureResponse("Unable to cancel payment", exception.getMessage());
        }
    }
}
