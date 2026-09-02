package com.fitouts.checklist.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.checklist.dto.SiteVisitChecklistScopeRequest;
import com.fitouts.checklist.dto.SiteVisitCreateRequest;
import com.fitouts.checklist.dto.SiteVisitEstimateRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsRequest;
import com.fitouts.checklist.dto.SiteVisitReportRequest;
import com.fitouts.checklist.dto.SiteVisitResponse;
import com.fitouts.checklist.service.SiteVisitEstimateEmailService;
import com.fitouts.checklist.service.SiteVisitEstimateService;
import com.fitouts.checklist.service.SiteVisitRecordingService;
import com.fitouts.checklist.service.SiteVisitReportService;
import com.fitouts.checklist.service.SiteVisitService;
import com.fitouts.shared.api.BaseController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/site-visits")
@Validated
@RequiredArgsConstructor
public class SiteVisitController extends BaseController {

    private final SiteVisitService siteVisitService;
    private final SiteVisitReportService siteVisitReportService;
    private final SiteVisitEstimateService siteVisitEstimateService;
    private final SiteVisitRecordingService recordingService;
    private final SiteVisitEstimateEmailService estimateEmailService;

    @PostMapping("/CreateSite-Visits")
    public ResponseEntity<?> create(@Valid @RequestBody SiteVisitCreateRequest request) {
        try {
            return successResponse("Site visit scheduled successfully", siteVisitService.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to schedule site visit", exception.getMessage());
        }
    }

    @GetMapping("GetAllSite-Visits")
    public ResponseEntity<?> getAll() {
        try {
            List<SiteVisitResponse> siteVisits = siteVisitService.getAll();
            return successResponse(siteVisits);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch site visits", exception.getMessage());
        }
    }

    @GetMapping("/GetSiteVisitByUuid/{uuid}")
    public ResponseEntity<?> getByUuid(@PathVariable UUID uuid) {
        try {
            return successResponse(siteVisitService.getByUuid(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch site visit", exception.getMessage());
        }
    }

    @PostMapping("Site/{uuid}/location-details")
    public ResponseEntity<?> addLocationDetails(
            @PathVariable UUID uuid,
            @Valid @RequestBody SiteVisitLocationDetailsRequest request) {
        try {
            return successResponse("Site visit location details added successfully",
                    siteVisitService.addLocationDetails(uuid, request));
        } catch (Exception exception) {
            return failureResponse("Unable to add site visit location details", exception.getMessage());
        }
    }

    @PatchMapping("/{uuid}/checklist-scope")
    public ResponseEntity<?> updateChecklistScope(
            @PathVariable UUID uuid,
            @Valid @RequestBody SiteVisitChecklistScopeRequest request) {
        try {
            return successResponse(
                    "Checklist scope updated successfully",
                    siteVisitService.updateChecklistScope(uuid, request));
        } catch (Exception exception) {
            return failureResponse("Unable to update checklist scope", exception.getMessage());
        }
    }

    @PostMapping("/EmployeeSiteVisitByUuid/{uuid}/report")
    public ResponseEntity<?> submitReport(
            @PathVariable UUID uuid,
            @Valid @RequestBody SiteVisitReportRequest request) {
        try {
            return successResponse(
                    "Site visit report submitted successfully",
                    siteVisitReportService.submit(uuid, request));
        } catch (Exception exception) {
            return failureResponse("Unable to submit site visit report", exception.getMessage());
        }
    }

    @GetMapping("/{uuid}/report")
    public ResponseEntity<?> getReport(@PathVariable UUID uuid) {
        try {
            return successResponse(siteVisitReportService.getBySiteVisitUuid(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch site visit report", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/recordings")
    public ResponseEntity<?> uploadRecording(
            @PathVariable UUID uuid,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {
        try {
            return successResponse(
                    "Recording uploaded successfully",
                    recordingService.upload(uuid, file, durationSeconds));
        } catch (Exception exception) {
            return failureResponse("Unable to upload recording", exception.getMessage());
        }
    }

    @GetMapping("/{uuid}/recordings")
    public ResponseEntity<?> listRecordings(@PathVariable UUID uuid) {
        try {
            return successResponse(recordingService.listByVisit(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch recordings", exception.getMessage());
        }
    }

    @PostMapping(value = "/{uuid}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPhoto(
            @PathVariable UUID uuid,
            @RequestParam("file") MultipartFile file) {
        try {
            return successResponse(
                    "Photo uploaded successfully",
                    recordingService.uploadPhoto(uuid, file));
        } catch (Exception exception) {
            return failureResponse("Unable to upload photo", exception.getMessage());
        }
    }

    @GetMapping("/{uuid}/estimate")
    public ResponseEntity<?> getEstimate(@PathVariable UUID uuid) {
        try {
            return successResponse(siteVisitEstimateService.getOrCreate(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to load site visit estimate", exception.getMessage());
        }
    }

    @PutMapping("/{uuid}/estimate")
    public ResponseEntity<?> upsertEstimate(
            @PathVariable UUID uuid,
            @Valid @RequestBody SiteVisitEstimateRequest request) {
        try {
            return successResponse(
                    "Rough estimate saved successfully",
                    siteVisitEstimateService.upsert(uuid, request));
        } catch (Exception exception) {
            return failureResponse("Unable to save site visit estimate", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/estimate/issue")
    public ResponseEntity<?> issueEstimate(@PathVariable UUID uuid) {
        try {
            return successResponse(
                    "Estimate issued successfully",
                    siteVisitEstimateService.issue(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to issue site visit estimate", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/estimate/stamp")
    public ResponseEntity<?> uploadEstimateStamp(
            @PathVariable UUID uuid,
            @RequestParam("file") MultipartFile file) {
        try {
            return successResponse("Stamp updated for this visit", siteVisitEstimateService.uploadStamp(uuid, file));
        } catch (Exception exception) {
            return failureResponse("Unable to upload stamp", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/estimate/signature")
    public ResponseEntity<?> uploadEstimateSignature(
            @PathVariable UUID uuid,
            @RequestParam("file") MultipartFile file) {
        try {
            return successResponse(
                    "Signature updated for this visit",
                    siteVisitEstimateService.uploadSignature(uuid, file));
        } catch (Exception exception) {
            return failureResponse("Unable to upload signature", exception.getMessage());
        }
    }

    @DeleteMapping("/{uuid}/estimate/stamp")
    public ResponseEntity<?> clearEstimateStamp(@PathVariable UUID uuid) {
        try {
            return successResponse("Using company stamp", siteVisitEstimateService.clearStamp(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to clear stamp", exception.getMessage());
        }
    }

    @DeleteMapping("/{uuid}/estimate/signature")
    public ResponseEntity<?> clearEstimateSignature(@PathVariable UUID uuid) {
        try {
            return successResponse("Using company signature", siteVisitEstimateService.clearSignature(uuid));
        } catch (Exception exception) {
            return failureResponse("Unable to clear signature", exception.getMessage());
        }
    }

    @PostMapping("/{uuid}/estimate/send")
    public ResponseEntity<?> sendEstimateEmail(
            @PathVariable UUID uuid,
            @RequestParam String recipientEmail,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String messageBody,
            @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {
        try {
            estimateEmailService.sendToClient(uuid, recipientEmail, subject, messageBody, attachments);
            return successResponse("Email sent successfully", null);
        } catch (Exception exception) {
            return failureResponse("Unable to send email", exception.getMessage());
        }
    }

    @GetMapping("/estimates/issued")
    public ResponseEntity<?> listIssuedEstimatesForClient() {
        try {
            return successResponse(siteVisitEstimateService.listIssuedForCurrentClient());
        } catch (Exception exception) {
            return failureResponse("Unable to fetch issued estimates", exception.getMessage());
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<?> getMyAssignedVisits() {
        try {
            return successResponse(siteVisitService.getMyAssignedVisits());
        } catch (Exception exception) {
            return failureResponse("Unable to fetch assigned site visits", exception.getMessage());
        }
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeSiteVisits(@PathVariable Long employeeId) {
        try {
            return successResponse(siteVisitService.getEmployeeSiteVisits(employeeId));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch site visits", exception.getMessage());
        }
    }
}
