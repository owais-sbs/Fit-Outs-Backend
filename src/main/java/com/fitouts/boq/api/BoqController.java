package com.fitouts.boq.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fitouts.boq.application.BoqApprovalService;
import com.fitouts.boq.application.BoqService;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/boq")
@RequiredArgsConstructor
public class BoqController extends BaseController {

    private final BoqService boqService;
    private final BoqApprovalService boqApprovalService;

    @PostMapping("/generate-from-qto/{sessionId}")
    public ResponseEntity<?> generateFromQto(@PathVariable UUID sessionId) {
        try {
            return successResponse("BOQ generated", boqService.generateFromQto(sessionId));
        } catch (Exception e) {
            return failureResponse("Failed to generate BOQ", e.getMessage());
        }
    }

    @PostMapping("/from-survey")
    public ResponseEntity<?> saveFromSurvey(@RequestBody BoqSurveySaveRequest request) {
        try {
            return successResponse("BOQ saved", boqService.saveFromSurvey(request));
        } catch (Exception e) {
            return failureResponse("Failed to save BOQ", e.getMessage());
        }
    }

    @GetMapping("/inbox")
    public ResponseEntity<?> inbox(@RequestParam(required = false) String role) {
        try {
            return successResponse(boqApprovalService.listPendingForCurrentUser(role));
        } catch (Exception e) {
            return failureResponse("Failed to fetch BOQ inbox", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        try {
            return successResponse(boqService.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch BOQ", e.getMessage());
        }
    }

    @GetMapping("/{id}/approval-history")
    public ResponseEntity<?> approvalHistory(@PathVariable UUID id) {
        try {
            return successResponse(boqApprovalService.getApprovalHistory(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch approval history", e.getMessage());
        }
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<?> listByProject(@PathVariable Long projectId) {
        try {
            return successResponse(boqService.listByProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list BOQs", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody BoqUpdateRequest request) {
        try {
            return successResponse("BOQ updated", boqService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update BOQ", e.getMessage());
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(@PathVariable UUID id) {
        try {
            return successResponse("BOQ submitted for approval", boqApprovalService.submitForApproval(id));
        } catch (Exception e) {
            return failureResponse("Failed to submit BOQ", e.getMessage());
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID id, @RequestBody(required = false) BoqApprovalActionRequest request) {
        try {
            String comments = request != null ? request.getComments() : null;
            return successResponse("BOQ approved", boqApprovalService.approve(id, comments));
        } catch (Exception e) {
            return failureResponse("Failed to approve BOQ", e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID id, @RequestBody BoqApprovalActionRequest request) {
        try {
            return successResponse("BOQ rejected", boqApprovalService.reject(id, request != null ? request.getComments() : null));
        } catch (Exception e) {
            return failureResponse("Failed to reject BOQ", e.getMessage());
        }
    }

    @PostMapping("/{id}/revisions")
    public ResponseEntity<?> createRevision(@PathVariable UUID id, @RequestBody(required = false) BoqRevisionCreateRequest request) {
        try {
            String label = request != null ? request.getRevisionLabel() : null;
            return successResponse("BOQ revision created", boqApprovalService.createRevision(id, label));
        } catch (Exception e) {
            return failureResponse("Failed to create revision", e.getMessage());
        }
    }

    /** @deprecated use POST /{id}/submit */
    @PostMapping("/{id}/finalize")
    public ResponseEntity<?> finalizeBoq(@PathVariable UUID id) {
        try {
            return successResponse("BOQ submitted for approval", boqApprovalService.submitForApproval(id));
        } catch (Exception e) {
            return failureResponse("Failed to finalize BOQ", e.getMessage());
        }
    }
}
