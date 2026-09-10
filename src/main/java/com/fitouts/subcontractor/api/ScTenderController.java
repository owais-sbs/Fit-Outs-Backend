package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScTenderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}/sc-packages/{packageUuid}/tender")
@RequiredArgsConstructor
public class ScTenderController extends BaseController {

    private final ScTenderService tenderService;

    @GetMapping("/eligible-bidders")
    public Object listEligibleBidders(@PathVariable Long projectId, @PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.listEligibleBidders(projectId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to list eligible bidders", e.getMessage());
        }
    }

    @GetMapping("/bidders")
    public Object listBidders(@PathVariable Long projectId, @PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.listBidders(projectId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to list bidders", e.getMessage());
        }
    }

    @PostMapping("/bidders")
    public Object addBidders(
            @PathVariable Long projectId,
            @PathVariable UUID packageUuid,
            @RequestBody ScAddBiddersRequest request) {
        try {
            return successResponse(tenderService.addBidders(projectId, packageUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to add bidders", e.getMessage());
        }
    }

    @PostMapping("/issue")
    public Object issueRfq(
            @PathVariable Long projectId,
            @PathVariable UUID packageUuid,
            @RequestBody ScIssueRfqRequest request) {
        try {
            return successResponse(tenderService.issueRfq(projectId, packageUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to issue RFQ", e.getMessage());
        }
    }

    @GetMapping("/clarifications")
    public Object listClarifications(@PathVariable Long projectId, @PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.listClarifications(projectId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to list clarifications", e.getMessage());
        }
    }

    @PostMapping("/clarifications/{clarificationUuid}/answer")
    public Object answerClarification(
            @PathVariable Long projectId,
            @PathVariable UUID packageUuid,
            @PathVariable UUID clarificationUuid,
            @RequestBody ScClarificationAnswerRequest request) {
        try {
            return successResponse(tenderService.answerClarification(
                    projectId, packageUuid, clarificationUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to answer clarification", e.getMessage());
        }
    }

    @GetMapping("/comparison")
    public Object getComparison(@PathVariable Long projectId, @PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.getComparison(projectId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load comparison", e.getMessage());
        }
    }

    @PostMapping("/award")
    public Object awardPackage(
            @PathVariable Long projectId,
            @PathVariable UUID packageUuid,
            @RequestBody ScAwardRequest request) {
        try {
            return successResponse(tenderService.awardPackage(projectId, packageUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to award package", e.getMessage());
        }
    }

    @GetMapping("/award-pack")
    public Object getAwardPack(@PathVariable Long projectId, @PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.getAwardPack(projectId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load award pack", e.getMessage());
        }
    }
}
