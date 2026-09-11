package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScWave7CommercialService;
import com.fitouts.subcontractor.application.SubcontractorPortalService;
import com.fitouts.subcontractor.application.SubcontractorService;
import com.fitouts.subcontractor.domain.ScSiteReportType;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SubcontractorController extends BaseController {

    private final SubcontractorService subcontractorService;
    private final SubcontractorPortalService portalService;
    private final ScWave7CommercialService commercialService;

    @GetMapping("/api/projects/{projectId}/sc-packages")
    public Object listPackages(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.listPackages(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list subcontractor packages", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-packages/{uuid}")
    public Object getPackage(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.getPackage(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to load subcontractor package", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-packages/generate-from-boq")
    public Object generateFromBoq(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.generateFromBoq(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to generate packages from BOQ", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-packages")
    public Object createPackage(@PathVariable Long projectId, @RequestBody SubcontractorPackageRequest request) {
        try {
            return successResponse(subcontractorService.createPackage(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create subcontractor package", e.getMessage());
        }
    }

    @PutMapping("/api/projects/{projectId}/sc-packages/{uuid}")
    public Object updatePackage(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody SubcontractorPackageRequest request) {
        try {
            return successResponse(subcontractorService.updatePackage(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update subcontractor package", e.getMessage());
        }
    }

    @DeleteMapping("/api/projects/{projectId}/sc-packages/{uuid}")
    public Object deletePackage(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            subcontractorService.deletePackage(projectId, uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete subcontractor package", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-packages/{uuid}/appoint")
    public Object appoint(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody AppointSubcontractorRequest request) {
        try {
            return successResponse(subcontractorService.appoint(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to appoint subcontractor", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/my-packages")
    public Object myPackages() {
        try {
            return successResponse(subcontractorService.myPackages());
        } catch (Exception e) {
            return failureResponse("Failed to load my packages", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/my-projects")
    public Object myProjects() {
        try {
            return successResponse(subcontractorService.myProjects());
        } catch (Exception e) {
            return failureResponse("Failed to load my projects", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/projects/{projectId}")
    public Object getMyProject(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.getMyProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load project", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/packages/{uuid}/accept")
    public Object acceptPackage(@PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.acceptPackage(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to accept package", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/packages/{uuid}/claims")
    public Object createClaim(@PathVariable UUID uuid, @RequestBody SubcontractorClaimRequest request) {
        try {
            return successResponse(subcontractorService.createClaim(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to create claim", e.getMessage());
        }
    }

    @GetMapping("/api/subcontractor/packages/{uuid}/claims")
    public Object listClaims(@PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.listClaimsForPackage(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to list claims", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-claims")
    public Object listProjectClaims(@PathVariable Long projectId) {
        try {
            return successResponse(subcontractorService.listClaimsForProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list project claims", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/claims/{uuid}/submit")
    public Object submitClaim(@PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.submitClaim(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit claim", e.getMessage());
        }
    }

    @PostMapping(value = "/api/subcontractor/claims/{uuid}/attachments", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadClaimAttachment(
            @PathVariable UUID uuid,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            return successResponse(subcontractorService.uploadClaimAttachment(uuid, file));
        } catch (Exception e) {
            return failureResponse("Failed to upload claim attachment", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-claims/{uuid}/approve")
    public Object approveClaim(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(subcontractorService.approveClaim(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve claim", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-claims/{uuid}/reject")
    public Object rejectClaim(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) ClaimRejectRequest request) {
        try {
            return successResponse(subcontractorService.rejectClaim(projectId, uuid,
                    request != null ? request : new ClaimRejectRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reject claim", e.getMessage());
        }
    }

    // ── Variations ───────────────────────────────────────────────────────────

    @GetMapping("/api/subcontractor/variations")
    public Object myVariations() {
        try {
            return successResponse(portalService.myVariations());
        } catch (Exception e) {
            return failureResponse("Failed to load variations", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/packages/{uuid}/variations")
    public Object createVariation(@PathVariable UUID uuid, @RequestBody ScVariationRequestBody request) {
        try {
            return successResponse(portalService.createVariation(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to create variation", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/variations/{uuid}/submit")
    public Object submitVariation(@PathVariable UUID uuid) {
        try {
            return successResponse(portalService.submitVariation(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit variation", e.getMessage());
        }
    }

    @PostMapping(value = "/api/subcontractor/variations/{uuid}/attachments",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadVariationAttachment(
            @PathVariable UUID uuid,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            return successResponse(portalService.uploadVariationAttachment(uuid, file));
        } catch (Exception e) {
            return failureResponse("Failed to upload variation attachment", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-variations")
    public Object listProjectVariations(@PathVariable Long projectId) {
        try {
            return successResponse(portalService.listVariationsForProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list variations", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-variations/{uuid}/approve")
    public Object approveVariation(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(portalService.approveVariation(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve variation", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-variations/{uuid}/reject")
    public Object rejectVariation(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) ClaimRejectRequest request) {
        try {
            return successResponse(portalService.rejectVariation(projectId, uuid,
                    request != null ? request : new ClaimRejectRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reject variation", e.getMessage());
        }
    }

    // ── Site reports ─────────────────────────────────────────────────────────

    @GetMapping("/api/subcontractor/site-reports")
    public Object mySiteReports(@RequestParam(required = false) String type) {
        try {
            ScSiteReportType reportType = type != null && !type.isBlank()
                    ? ScSiteReportType.valueOf(type.trim().toUpperCase()) : null;
            return successResponse(portalService.mySiteReports(reportType));
        } catch (Exception e) {
            return failureResponse("Failed to load site reports", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/site-reports")
    public Object createSiteReport(@RequestBody ScSiteReportRequestBody request) {
        try {
            return successResponse(portalService.createSiteReport(request));
        } catch (Exception e) {
            return failureResponse("Failed to create site report", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-site-reports/{uuid}/acknowledge")
    public Object acknowledgeSiteReport(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(portalService.acknowledgeSiteReport(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to acknowledge site report", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-site-reports/{uuid}/resolve")
    public Object resolveSiteReport(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) ClaimRejectRequest request) {
        try {
            String notes = request != null ? request.getReason() : null;
            return successResponse(portalService.resolveSiteReport(projectId, uuid, notes));
        } catch (Exception e) {
            return failureResponse("Failed to resolve site report", e.getMessage());
        }
    }

    // ── Invoices / payments ──────────────────────────────────────────────────

    @GetMapping("/api/subcontractor/invoices")
    public Object myInvoices() {
        try {
            return successResponse(portalService.myInvoices());
        } catch (Exception e) {
            return failureResponse("Failed to load invoices", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/invoices")
    public Object createInvoice(@RequestBody ScInvoiceRequestBody request) {
        try {
            return successResponse(portalService.createInvoice(request));
        } catch (Exception e) {
            return failureResponse("Failed to create invoice", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractor/invoices/{uuid}/submit")
    public Object submitInvoice(@PathVariable UUID uuid) {
        try {
            return successResponse(portalService.submitInvoice(uuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit invoice", e.getMessage());
        }
    }

    @PostMapping(value = "/api/subcontractor/invoices/{uuid}/attachments",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadInvoiceAttachment(
            @PathVariable UUID uuid,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            return successResponse(portalService.uploadInvoiceAttachment(uuid, file));
        } catch (Exception e) {
            return failureResponse("Failed to upload invoice attachment", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-invoices/{uuid}/approve")
    public Object approveInvoice(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(portalService.approveInvoice(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to approve invoice", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-invoices/{uuid}/reject")
    public Object rejectInvoice(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) ClaimRejectRequest request) {
        try {
            return successResponse(portalService.rejectInvoice(projectId, uuid,
                    request != null ? request : new ClaimRejectRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reject invoice", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-invoices/{uuid}/mark-paid")
    public Object markInvoicePaid(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) ScMarkPaidRequest request) {
        try {
            return successResponse(portalService.markInvoicePaid(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to mark invoice paid", e.getMessage());
        }
    }

    // ── BOQ view ─────────────────────────────────────────────────────────────

    @GetMapping("/api/subcontractor/boq-lines")
    public Object myBoqLines(@RequestParam(required = false) Long projectId) {
        try {
            return successResponse(portalService.myBoqLines(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load BOQ lines", e.getMessage());
        }
    }

    // ── Wave 7 commercial (staff) ───────────────────────────────────────────

    @PostMapping("/api/projects/{projectId}/sc-claims/{uuid}/measure")
    public Object measureClaim(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody ScMeasureClaimRequest request) {
        try {
            return successResponse(commercialService.measureClaim(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to measure claim", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-claims/{uuid}/certify")
    public Object certifyClaim(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestBody(required = false) ScCertifyClaimRequest request) {
        try {
            return successResponse(commercialService.certifyClaim(projectId, uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to certify claim", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-claims/{uuid}/mark-paid")
    public Object markClaimPaid(
            @PathVariable Long projectId,
            @PathVariable UUID uuid,
            @RequestParam(required = false) String accountingRef) {
        try {
            return successResponse(commercialService.markClaimPaid(projectId, uuid, accountingRef));
        } catch (Exception e) {
            return failureResponse("Failed to mark claim paid", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-certificates")
    public Object listCertificates(@PathVariable Long projectId) {
        try {
            return successResponse(commercialService.listCertificatesForProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list certificates", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-retention")
    public Object listRetention(
            @PathVariable Long projectId,
            @RequestParam(required = false) UUID packageUuid) {
        try {
            return successResponse(commercialService.listRetentionLedger(projectId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to list retention ledger", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/sc-back-charges")
    public Object listBackCharges(
            @PathVariable Long projectId,
            @RequestParam(required = false) UUID packageUuid) {
        try {
            return successResponse(commercialService.listBackChargesForProject(projectId, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to list back charges", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/sc-back-charges")
    public Object createBackCharge(
            @PathVariable Long projectId,
            @RequestBody ScBackChargeRequest request) {
        try {
            return successResponse(commercialService.createBackCharge(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create back charge", e.getMessage());
        }
    }

    @PostMapping("/api/subcontractors/{organizationUuid}/scorecard")
    public Object computeScorecard(
            @PathVariable UUID organizationUuid,
            @RequestParam(required = false) UUID packageUuid) {
        try {
            return successResponse(commercialService.computeAndSaveScorecard(organizationUuid, packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to compute scorecard", e.getMessage());
        }
    }

    // ── Snags (SC portal) ────────────────────────────────────────────────────

    @GetMapping("/api/subcontractor/snags")
    public Object mySnags() {
        try {
            return successResponse(portalService.listMySnags());
        } catch (Exception e) {
            return failureResponse("Failed to load snags", e.getMessage());
        }
    }
}
