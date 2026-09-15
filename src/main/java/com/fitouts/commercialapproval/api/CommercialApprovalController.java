package com.fitouts.commercialapproval.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.commercialapproval.application.CommercialApprovalService;
import com.fitouts.commercialapproval.domain.CommercialEventType;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CommercialApprovalController extends BaseController {

    private final CommercialApprovalService commercialApprovalService;

    @GetMapping("/api/commercial-approvals/matrices")
    public Object listMatrices() {
        try {
            return successResponse(commercialApprovalService.listMatrices());
        } catch (Exception e) {
            return failureResponse("Failed to list matrices", e.getMessage());
        }
    }

    @PutMapping("/api/commercial-approvals/matrices")
    public Object upsertMatrix(@RequestBody MatrixUpsertRequest request) {
        try {
            return successResponse(commercialApprovalService.upsertMatrix(request));
        } catch (Exception e) {
            return failureResponse("Failed to save matrix", e.getMessage());
        }
    }

    @GetMapping("/api/commercial-approvals/inbox")
    public Object inbox() {
        try {
            return successResponse(commercialApprovalService.inbox());
        } catch (Exception e) {
            return failureResponse("Failed to load inbox", e.getMessage());
        }
    }

    @GetMapping("/api/commercial-approvals/runs/{runUuid}")
    public Object getRun(@PathVariable UUID runUuid) {
        try {
            return successResponse(commercialApprovalService.getRun(runUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load run", e.getMessage());
        }
    }

    @PostMapping("/api/commercial-approvals/tasks/{taskUuid}/approve")
    public Object approveTask(@PathVariable UUID taskUuid, @RequestBody(required = false) TaskDecisionRequest request) {
        try {
            return successResponse(commercialApprovalService.approveTask(taskUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to approve task", e.getMessage());
        }
    }

    @PostMapping("/api/commercial-approvals/tasks/{taskUuid}/reject")
    public Object rejectTask(@PathVariable UUID taskUuid, @RequestBody TaskDecisionRequest request) {
        try {
            return successResponse(commercialApprovalService.rejectTask(taskUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to reject task", e.getMessage());
        }
    }

    @GetMapping("/api/commercial-approvals/export")
    public ResponseEntity<String> export(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) CommercialEventType eventType) {
        String csv = commercialApprovalService.exportCsv(from, to, eventType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"commercial-approvals.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
