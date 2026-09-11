package com.fitouts.approval.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.approval.application.ApprovalCaseService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ApprovalCaseController extends BaseController {

    private final ApprovalCaseService caseService;

    @PostMapping("/api/projects/{projectId}/approvals/resolve")
    public Object resolve(@PathVariable Long projectId,
                          @RequestBody(required = false) ApprovalResolveRequest request) {
        try {
            return successResponse(caseService.resolve(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to resolve approvals", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/approvals/generate")
    public Object generate(@PathVariable Long projectId,
                           @RequestBody(required = false) ApprovalResolveRequest request) {
        try {
            return successResponse("Approval permits generated", caseService.generate(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to generate approvals", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/approvals/cases")
    public Object addCase(@PathVariable Long projectId, @RequestBody AddPermitRequest request) {
        try {
            return successResponse("Permit added", caseService.addCase(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to add permit", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/approvals")
    public Object listForProject(@PathVariable Long projectId) {
        try {
            return successResponse(caseService.listForProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load project approvals", e.getMessage());
        }
    }

    @GetMapping("/api/approvals/dashboard")
    public Object dashboard(@RequestParam(required = false) String status,
                            @RequestParam(required = false) String authorityCode,
                            @RequestParam(required = false) Long ownerAccountId) {
        try {
            return successResponse(caseService.dashboard(status, authorityCode, ownerAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to load approvals dashboard", e.getMessage());
        }
    }

    @GetMapping("/api/approval-cases/{caseUuid}")
    public Object get(@PathVariable UUID caseUuid) {
        try {
            return successResponse(caseService.get(caseUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load permit", e.getMessage());
        }
    }

    @DeleteMapping("/api/approval-cases/{caseUuid}")
    public Object deleteIfNotStarted(@PathVariable UUID caseUuid) {
        try {
            return successResponse("Permit removed", caseService.deleteIfNotStarted(caseUuid));
        } catch (Exception e) {
            return failureResponse("Failed to remove permit", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/authority")
    public Object bindAuthority(@PathVariable UUID caseUuid, @RequestBody BindAuthorityRequest request) {
        try {
            return successResponse("Authority saved", caseService.bindAuthority(caseUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to set authority", e.getMessage());
        }
    }

    @PatchMapping("/api/approval-cases/{caseUuid}")
    public Object patch(@PathVariable UUID caseUuid, @RequestBody CaseStatusPatchRequest request) {
        try {
            return successResponse(caseService.patch(caseUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update permit", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/checklist/{itemUuid}/attach")
    public Object attach(@PathVariable UUID caseUuid, @PathVariable UUID itemUuid,
                         @RequestBody ChecklistAttachRequest request) {
        try {
            return successResponse(caseService.attachChecklistItem(caseUuid, itemUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to attach document", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/checklist/{itemUuid}/waive")
    public Object waive(@PathVariable UUID caseUuid, @PathVariable UUID itemUuid,
                        @RequestBody ChecklistAttachRequest request) {
        try {
            return successResponse(caseService.waiveChecklistItem(caseUuid, itemUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to waive document", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/assemble-pack")
    public Object assemblePack(@PathVariable UUID caseUuid) {
        try {
            return successResponse(caseService.assemblePack(caseUuid));
        } catch (Exception e) {
            return failureResponse("Failed to assemble pack", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/submissions")
    public Object addSubmission(@PathVariable UUID caseUuid, @RequestBody(required = false) CaseSubmissionRequest request) {
        try {
            return successResponse(caseService.addSubmission(caseUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to record submission", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/comments")
    public Object addComment(@PathVariable UUID caseUuid, @RequestBody CaseCommentRequest request) {
        try {
            return successResponse(caseService.addComment(caseUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to record comment", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/fees")
    public Object saveFee(@PathVariable UUID caseUuid, @RequestBody CaseFeeRequest request) {
        try {
            return successResponse(caseService.saveFee(caseUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to save fee", e.getMessage());
        }
    }

    @PostMapping("/api/approval-cases/{caseUuid}/renew")
    public Object renew(@PathVariable UUID caseUuid) {
        try {
            return successResponse("Renewal permit created", caseService.renew(caseUuid));
        } catch (Exception e) {
            return failureResponse("Failed to open renewal", e.getMessage());
        }
    }

    @GetMapping("/api/deposits")
    public Object deposits(@RequestParam(required = false) String status) {
        try {
            return successResponse(caseService.deposits(!"all".equalsIgnoreCase(status)));
        } catch (Exception e) {
            return failureResponse("Failed to load deposits", e.getMessage());
        }
    }

    @GetMapping("/api/approvals/analytics/sla")
    public Object slaAnalytics() {
        try {
            return successResponse(caseService.slaAnalytics());
        } catch (Exception e) {
            return failureResponse("Failed to load SLA analytics", e.getMessage());
        }
    }
}
