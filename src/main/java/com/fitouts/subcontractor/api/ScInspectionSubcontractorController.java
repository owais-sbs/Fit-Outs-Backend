package com.fitouts.subcontractor.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScInspectionRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subcontractor/packages/{packageUuid}/inspections")
@RequiredArgsConstructor
public class ScInspectionSubcontractorController extends BaseController {

    private final ScInspectionRequestService inspectionService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitInspectionJson(
            @PathVariable UUID packageUuid,
            @Valid @RequestBody ScInspectionSubmitRequest jsonRequest) {
        try {
            return successResponse("Inspection request submitted",
                    inspectionService.submitInspection(packageUuid, jsonRequest, null));
        } catch (Exception e) {
            return failureResponse("Failed to submit inspection request", e.getMessage());
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitInspectionMultipart(
            @PathVariable UUID packageUuid,
            @RequestParam(value = "inspectionType", required = false) String inspectionType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "noticePeriodHours", required = false) Integer noticePeriodHours,
            @RequestParam(value = "activityUuid", required = false) UUID activityUuid,
            @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) List<MultipartFile> singleFiles) {
        try {
            ScInspectionSubmitRequest request = new ScInspectionSubmitRequest();
            request.setInspectionType(inspectionType);
            request.setDescription(description);
            request.setNoticePeriodHours(noticePeriodHours);
            request.setActivityUuid(activityUuid);

            List<MultipartFile> uploadedFiles = new java.util.ArrayList<>();
            if (attachments != null) uploadedFiles.addAll(attachments);
            if (files != null) uploadedFiles.addAll(files);
            if (singleFiles != null) uploadedFiles.addAll(singleFiles);

            return successResponse("Inspection request submitted",
                    inspectionService.submitInspection(packageUuid, request, uploadedFiles));
        } catch (Exception e) {
            return failureResponse("Failed to submit inspection request", e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listMyInspections(@PathVariable UUID packageUuid) {
        try {
            return successResponse(inspectionService.listMyInspections(packageUuid));
        } catch (Exception e) {
            return failureResponse("Failed to list inspection requests", e.getMessage());
        }
    }
}
