package com.fitouts.subcontractor.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScCompanyProfileService;
import com.fitouts.subcontractor.domain.ScComplianceDocType;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScCompanyProfileController extends BaseController {

    private final ScCompanyProfileService profileService;

    @GetMapping("/api/subcontractor/company-profile")
    public Object getMyProfile() {
        try {
            return successResponse(profileService.getMyProfile());
        } catch (Exception e) {
            return failureResponse("Failed to load company profile", e.getMessage());
        }
    }

    @PutMapping("/api/subcontractor/company-profile")
    public Object updateMyProfile(@RequestBody ScCompanyProfileRequest request) {
        try {
            return successResponse(profileService.updateMyProfile(request));
        } catch (Exception e) {
            return failureResponse("Failed to update company profile", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/company-profile/trade-categories")
    public Object tradeCategories() {
        try {
            return successResponse(profileService.tradeCategoryOptions());
        } catch (Exception e) {
            return failureResponse("Failed to load trade categories", e.getMessage());
        }
    }

    @PostMapping(value = "/api/subcontractor/company-profile/trade-licence/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadTradeLicence(@RequestParam("file") MultipartFile file) {
        try {
            return successResponse(profileService.uploadTradeLicence(file));
        } catch (Exception e) {
            return failureResponse("Failed to upload trade licence", e.getMessage());
        }
    }

    @PutMapping("/api/subcontractor/company-profile/compliance/{docType}")
    public Object upsertCompliance(
            @PathVariable String docType,
            @RequestBody ScComplianceDocumentRequest request) {
        try {
            return successResponse(profileService.upsertComplianceDocument(parseDocType(docType), request));
        } catch (Exception e) {
            return failureResponse("Failed to save compliance document", e.getMessage());
        }
    }

    @PostMapping(value = "/api/subcontractor/company-profile/compliance/{docType}/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadCompliance(
            @PathVariable String docType,
            @RequestParam("file") MultipartFile file) {
        try {
            return successResponse(profileService.uploadComplianceDocument(parseDocType(docType), file));
        } catch (Exception e) {
            return failureResponse("Failed to upload compliance document", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/workers")
    public Object listWorkers() {
        try {
            return successResponse(profileService.listMyWorkers());
        } catch (Exception e) {
            return failureResponse("Failed to load workers", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/workers")
    public Object createWorker(@RequestBody ScWorkerRequest request) {
        try {
            return successResponse(profileService.createWorker(request));
        } catch (Exception e) {
            return failureResponse("Failed to create worker", e.getMessage());
        }
    }

    @PutMapping("/api/subcontractor/workers/{uuid}")
    public Object updateWorker(@PathVariable java.util.UUID uuid, @RequestBody ScWorkerRequest request) {
        try {
            return successResponse(profileService.updateWorker(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update worker", e.getMessage());
        }
    }

    @DeleteMapping("/api/subcontractor/workers/{uuid}")
    public Object deleteWorker(@PathVariable java.util.UUID uuid) {
        try {
            profileService.deleteWorker(uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete worker", e.getMessage());
        }
    }

    @GetMapping("/api/sc-companies/{accountId}/appointment-eligibility")
    public Object appointmentEligibility(
            @PathVariable Long accountId,
            @RequestParam java.util.UUID packageUuid) {
        try {
            return successResponse(profileService.checkAppointmentEligibility(accountId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to check appointment eligibility", e.getMessage());
        }
    }

    private static ScComplianceDocType parseDocType(String raw) {
        return ScComplianceDocType.valueOf(raw.trim().toUpperCase());
    }
}
