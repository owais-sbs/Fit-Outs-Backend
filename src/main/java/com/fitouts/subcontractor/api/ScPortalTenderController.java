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
@RequestMapping("/api/subcontractor/tender")
@RequiredArgsConstructor
public class ScPortalTenderController extends BaseController {

    private final ScTenderService tenderService;

    @GetMapping("/rfqs")
    public Object listMyRfqs() {
        try {
            return successResponse(tenderService.listMyRfqs());
        } catch (Exception e) {
            return failureResponse("Failed to load RFQs", e.getMessage());
        }
    }

    @GetMapping("/rfqs/{packageUuid}")
    public Object getRfq(@PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.getRfqForBidder(packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load RFQ", e.getMessage());
        }
    }

    @PutMapping("/rfqs/{packageUuid}/quote")
    public Object saveQuoteDraft(@PathVariable UUID packageUuid, @RequestBody ScQuoteDraftRequest request) {
        try {
            return successResponse(tenderService.saveQuoteDraft(packageUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to save quote draft", e.getMessage());
        }
    }

    @PostMapping("/rfqs/{packageUuid}/quote/{quoteUuid}/submit")
    public Object submitQuote(@PathVariable UUID packageUuid, @PathVariable UUID quoteUuid) {
        try {
            return successResponse(tenderService.submitQuote(packageUuid, quoteUuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit quote", e.getMessage());
        }
    }

    @GetMapping("/bids")
    public Object getMyBids() {
        try {
            return successResponse(tenderService.getMyBids());
        } catch (Exception e) {
            return failureResponse("Failed to load bids", e.getMessage());
        }
    }

    @GetMapping("/rfqs/{packageUuid}/bids")
    public Object getMyBidsForPackage(@PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.getMyBidsForPackage(packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load bids", e.getMessage());
        }
    }

    @PostMapping("/rfqs/{packageUuid}/clarifications")
    public Object addClarification(@PathVariable UUID packageUuid, @RequestBody ScClarificationRequest request) {
        try {
            return successResponse(tenderService.addClarification(packageUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to add clarification", e.getMessage());
        }
    }

    @GetMapping("/rfqs/{packageUuid}/clarifications")
    public Object listMyClarifications(@PathVariable UUID packageUuid) {
        try {
            return successResponse(tenderService.listMyClarifications(packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load clarifications", e.getMessage());
        }
    }
}
