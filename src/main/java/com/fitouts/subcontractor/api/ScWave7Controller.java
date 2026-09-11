package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScWave7CommercialService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subcontractor/commercial")
@RequiredArgsConstructor
public class ScWave7Controller extends BaseController {

    private final ScWave7CommercialService commercialService;

    @GetMapping("/certificates")
    public Object listMyCertificates() {
        try {
            return successResponse(commercialService.listMyCertificates());
        } catch (Exception e) {
            return failureResponse("Failed to load certificates", e.getMessage());
        }
    }

    @GetMapping("/retention")
    public Object listMyRetention() {
        try {
            return successResponse(commercialService.listMyRetentionLedger());
        } catch (Exception e) {
            return failureResponse("Failed to load retention ledger", e.getMessage());
        }
    }

    @GetMapping("/back-charges")
    public Object listMyBackCharges() {
        try {
            return successResponse(commercialService.listMyBackCharges());
        } catch (Exception e) {
            return failureResponse("Failed to load back charges", e.getMessage());
        }
    }

    @PostMapping("/back-charges/{uuid}/acknowledge")
    public Object acknowledgeBackCharge(
            @PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean dispute) {
        try {
            return successResponse(commercialService.acknowledgeBackCharge(uuid, dispute));
        } catch (Exception e) {
            return failureResponse("Failed to acknowledge back charge", e.getMessage());
        }
    }

    @GetMapping("/scorecard")
    public Object listMyScorecards() {
        try {
            return successResponse(commercialService.listMyScorecards());
        } catch (Exception e) {
            return failureResponse("Failed to load scorecard", e.getMessage());
        }
    }

    @GetMapping("/scorecard/latest")
    public Object getLatestScorecard(@RequestParam(required = false) UUID packageUuid) {
        try {
            return successResponse(commercialService.getLatestScorecard(packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load scorecard", e.getMessage());
        }
    }

    @GetMapping("/claim-tracker")
    public Object getClaimStatusTracker() {
        try {
            return successResponse(commercialService.getClaimStatusTracker());
        } catch (Exception e) {
            return failureResponse("Failed to load claim tracker", e.getMessage());
        }
    }
}
