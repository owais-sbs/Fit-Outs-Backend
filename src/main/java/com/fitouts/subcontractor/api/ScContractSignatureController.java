package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScContractSignatureService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScContractSignatureController extends BaseController {

    private final ScContractSignatureService signatureService;

    // ── Admin Signature Endpoint ─────────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/sc-packages/{packageUuid}/admin-sign-contract")
    public ResponseEntity<?> adminSignContract(
            @PathVariable Long projectId,
            @PathVariable UUID packageUuid,
            @RequestBody(required = false) ScAdminSignContractRequest request,
            HttpServletRequest servletRequest) {
        try {
            return successResponse("Main Contractor signed subcontract agreement successfully",
                    signatureService.adminSignContract(projectId, packageUuid, request, servletRequest));
        } catch (Exception e) {
            return failureResponse("Failed to sign subcontract agreement as Main Contractor", e.getMessage());
        }
    }

    // ── Subcontractor Digital Signature Image Management ────────────────────

    @PostMapping(value = "/api/subcontractor/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSubcontractorSignature(@RequestParam("file") MultipartFile file) {
        try {
            return successResponse("Digital signature uploaded successfully",
                    signatureService.uploadSubcontractorSignature(file));
        } catch (Exception e) {
            return failureResponse("Failed to upload digital signature", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/signature")
    public ResponseEntity<?> getSubcontractorSignature() {
        try {
            return successResponse(signatureService.getSubcontractorSignature());
        } catch (Exception e) {
            return failureResponse("Failed to load digital signature", e.getMessage());
        }
    }

    // ── Subcontractor Contract View & Sign Endpoints ─────────────────────────

    @GetMapping("/api/subcontractor/packages/{packageUuid}/contract")
    public ResponseEntity<?> getContract(@PathVariable UUID packageUuid) {
        try {
            return successResponse(signatureService.getContract(packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to fetch subcontract agreement", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/packages/{packageUuid}/sign-contract")
    public ResponseEntity<?> signContract(
            @PathVariable UUID packageUuid,
            @Valid @RequestBody ScSignContractRequest request,
            HttpServletRequest servletRequest) {
        try {
            return successResponse("Subcontract agreement signed successfully",
                    signatureService.signContract(packageUuid, request, servletRequest));
        } catch (Exception e) {
            return failureResponse("Failed to sign subcontract agreement", e.getMessage());
        }
    }
}
