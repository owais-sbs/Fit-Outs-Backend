package com.fitouts.subcontractor.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.TenderWorkflowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TenderWorkflowController extends BaseController {

    private final TenderWorkflowService tenderWorkflowService;

    @PostMapping("/api/v1/projects/{projectId}/packages")
    public Object createPackage(
            @PathVariable Long projectId,
            @RequestBody TenderPackageRequest request) {
        try {
            Long currentAccountId = 1L; // Derived or default account ID
            return successResponse(tenderWorkflowService.createPackage(projectId, request, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to create tender package", e.getMessage());
        }
    }

    @GetMapping("/api/v1/projects/{projectId}/packages")
    public Object getProjectPackages(@PathVariable Long projectId) {
        try {
            return successResponse(tenderWorkflowService.getProjectPackages(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to get project tender packages", e.getMessage());
        }
    }

    @GetMapping("/api/v1/packages/{id}")
    public Object getPackageDetails(@PathVariable UUID id) {
        try {
            return successResponse(tenderWorkflowService.getPackageDetails(id));
        } catch (Exception e) {
            return failureResponse("Failed to get package details", e.getMessage());
        }
    }

    @PostMapping("/api/v1/packages/{id}/bidders")
    public Object addBidder(
            @PathVariable UUID id,
            @RequestBody PackageBidderRequest request) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.addBidder(id, request.getSubcontractorId(), currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to add bidder", e.getMessage());
        }
    }

    @GetMapping("/api/v1/packages/{id}/bidders")
    public Object getBidders(@PathVariable UUID id) {
        try {
            return successResponse(tenderWorkflowService.getBidders(id));
        } catch (Exception e) {
            return failureResponse("Failed to get bidders", e.getMessage());
        }
    }

    @PostMapping("/api/v1/packages/{id}/issue")
    public Object issuePackage(@PathVariable UUID id) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.issuePackage(id, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to issue package", e.getMessage());
        }
    }

    @GetMapping("/api/v1/sc/packages")
    public Object getScPackages(@RequestParam(value = "subcontractor_id") UUID subcontractorId) {
        try {
            return successResponse(tenderWorkflowService.getScPackages(subcontractorId));
        } catch (Exception e) {
            return failureResponse("Failed to get subcontractor packages", e.getMessage());
        }
    }

    @GetMapping("/api/v1/sc/packages/{id}/boq-lines")
    public Object getScPackageBoqLines(
            @PathVariable UUID id,
            @RequestParam(value = "subcontractor_id", required = false) UUID subcontractorId) {
        try {
            return successResponse(tenderWorkflowService.getScPackageBoqLines(id, subcontractorId));
        } catch (Exception e) {
            return failureResponse("Failed to get package BOQ lines", e.getMessage());
        }
    }

    @PostMapping("/api/v1/sc/quotes")
    public Object submitQuote(
            @RequestParam(value = "subcontractor_id") UUID subcontractorId,
            @RequestBody QuoteRequest request) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.submitQuote(subcontractorId, request, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to submit quote", e.getMessage());
        }
    }

    @PostMapping("/api/v1/sc/quotes/{id}/lines/bulk-upload")
    public Object bulkUploadQuoteLines(
            @PathVariable UUID id,
            @RequestParam(value = "subcontractor_id") UUID subcontractorId,
            @RequestBody List<QuoteLineRequest> lines) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.bulkUploadQuoteLines(id, subcontractorId, lines, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to bulk upload quote lines", e.getMessage());
        }
    }

    @PostMapping("/api/v1/packages/{id}/open-bids")
    public Object openBids(@PathVariable UUID id) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.openBids(id, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to open bids", e.getMessage());
        }
    }

    @GetMapping("/api/v1/packages/{id}/comparison")
    public Object getNormalizedComparison(@PathVariable UUID id) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.getNormalizedComparison(id, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to get normalized comparison", e.getMessage());
        }
    }

    @PostMapping("/api/v1/packages/{id}/clarifications")
    public Object createClarification(
            @PathVariable UUID id,
            @RequestBody PackageClarificationRequest request) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.createClarification(id, request, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to create clarification", e.getMessage());
        }
    }

    @GetMapping("/api/v1/packages/{id}/clarifications")
    public Object getClarifications(
            @PathVariable UUID id,
            @RequestParam(value = "subcontractor_id", required = false) UUID subcontractorId) {
        try {
            return successResponse(tenderWorkflowService.getClarifications(id, subcontractorId));
        } catch (Exception e) {
            return failureResponse("Failed to get clarifications", e.getMessage());
        }
    }

    @PostMapping("/api/v1/packages/{id}/addenda")
    public Object issueAddendum(
            @PathVariable UUID id,
            @RequestBody PackageAddendumRequest request) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.issueAddendum(id, request, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to issue addendum", e.getMessage());
        }
    }

    @PostMapping("/api/v1/packages/addenda/{addendumId}/ack")
    public Object acknowledgeAddendum(
            @PathVariable UUID addendumId,
            @RequestParam(value = "subcontractor_id") UUID subcontractorId) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.acknowledgeAddendum(addendumId, subcontractorId, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to acknowledge addendum", e.getMessage());
        }
    }

    @PostMapping("/api/v1/packages/{id}/award")
    public Object awardPackage(
            @PathVariable UUID id,
            @RequestBody PackageAwardRequest request) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.awardPackage(id, request, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to award package", e.getMessage());
        }
    }

    @GetMapping("/api/v1/packages/{id}/award-pack")
    public Object getAwardPack(@PathVariable UUID id) {
        try {
            Long currentAccountId = 1L;
            return successResponse(tenderWorkflowService.getAwardPack(id, currentAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to get award pack", e.getMessage());
        }
    }
}
