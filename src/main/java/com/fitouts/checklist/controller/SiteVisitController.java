package com.fitouts.checklist.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.checklist.dto.SiteVisitChecklistScopeRequest;
import com.fitouts.checklist.dto.SiteVisitCreateRequest;
import com.fitouts.checklist.dto.SiteVisitEstimateRequest;
import com.fitouts.checklist.dto.SiteVisitLocationDetailsRequest;
import com.fitouts.checklist.dto.SiteVisitReportRequest;
import com.fitouts.checklist.dto.SiteVisitResponse;
import com.fitouts.checklist.service.SiteVisitEstimateService;
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

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeSiteVisits(@PathVariable Long employeeId) {
        try {
            return successResponse(siteVisitService.getEmployeeSiteVisits(employeeId));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch site visits", exception.getMessage());
        }
    }
}
