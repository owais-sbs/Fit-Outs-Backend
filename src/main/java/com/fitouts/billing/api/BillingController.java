package com.fitouts.billing.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.billing.application.BillingService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BillingController extends BaseController {

    private final BillingService billingService;

    @GetMapping("/api/projects/{projectId}/billing-milestones")
    public Object listMilestones(@PathVariable Long projectId) {
        try {
            return successResponse(billingService.listMilestones(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list billing milestones", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/billing-milestones/{uuid}")
    public Object getMilestone(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(billingService.getMilestone(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to load billing milestone", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/billing-milestones")
    public Object createMilestone(@PathVariable Long projectId, @RequestBody BillingMilestoneRequest request) {
        try {
            return successResponse(billingService.createMilestone(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create billing milestone", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/billing-milestones/{uuid}")
    public Object updateMilestone(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody BillingMilestoneRequest request) {
        try {
            return successResponse(billingService.updateMilestone(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update billing milestone", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/billing-milestones/{uuid}")
    public Object deleteMilestone(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            billingService.deleteMilestone(projectId, uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete billing milestone", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/billing-milestones/{uuid}/request-payment")
    public Object requestPayment(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) RequestPaymentBody body) {
        try {
            return successResponse(billingService.requestPayment(projectId, uuid, body));
        } catch (Exception e) {
            return failureResponse("Failed to create payment request", e.getMessage());
        }
    }

    @PostMapping("/api/billing/payment-requests/{uuid}/submit")
    public Object submit(@PathVariable UUID uuid) {
        try {
            return successResponse(billingService.submit(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit payment request", e.getMessage());
        }
    }

    @PostMapping("/api/billing/payment-requests/{uuid}/approve")
    public Object approve(@PathVariable UUID uuid) {
        try {
            return successResponse(billingService.approve(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve payment request", e.getMessage());
        }
    }

    @PostMapping("/api/billing/payment-requests/{uuid}/reject")
    public Object reject(
            @PathVariable UUID uuid,
            @RequestBody(required = false) PaymentRejectRequest request) {
        try {
            return successResponse(billingService.reject(uuid,
                    request != null ? request : new PaymentRejectRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reject payment request", e.getMessage());
        }
    }

    @PostMapping("/api/billing/payment-requests/{uuid}/mark-paid")
    public Object markPaid(@PathVariable UUID uuid) {
        try {
            return successResponse(billingService.markPaid(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to mark payment request paid", e.getMessage());
        }
    }

    @GetMapping("/api/client/projects/{projectId}/invoices")
    public Object listClientInvoices(@PathVariable Long projectId) {
        try {
            return successResponse(billingService.listClientInvoices(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list invoices", e.getMessage());
        }
    }
}
