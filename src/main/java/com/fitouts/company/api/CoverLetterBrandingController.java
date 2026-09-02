package com.fitouts.company.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.company.application.CoverLetterBrandingService;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cover-letter-branding")
@RequiredArgsConstructor
public class CoverLetterBrandingController extends BaseController {

    private final CoverLetterBrandingService service;

    @GetMapping
    public ResponseEntity<?> get() {
        try {
            return successResponse(service.get());
        } catch (Exception e) {
            return failureResponse("Failed to load cover letter branding", e.getMessage());
        }
    }

    @PostMapping("/stamp")
    public ResponseEntity<?> uploadStamp(@RequestParam("file") MultipartFile file) {
        try {
            return successResponse("Stamp updated", service.uploadStamp(file));
        } catch (Exception e) {
            return failureResponse("Failed to upload stamp", e.getMessage());
        }
    }

    @PostMapping("/signature")
    public ResponseEntity<?> uploadSignature(@RequestParam("file") MultipartFile file) {
        try {
            return successResponse("Signature updated", service.uploadSignature(file));
        } catch (Exception e) {
            return failureResponse("Failed to upload signature", e.getMessage());
        }
    }

    @DeleteMapping("/stamp")
    public ResponseEntity<?> deleteStamp() {
        try {
            return successResponse("Stamp removed", service.deleteStamp());
        } catch (Exception e) {
            return failureResponse("Failed to remove stamp", e.getMessage());
        }
    }

    @DeleteMapping("/signature")
    public ResponseEntity<?> deleteSignature() {
        try {
            return successResponse("Signature removed", service.deleteSignature());
        } catch (Exception e) {
            return failureResponse("Failed to remove signature", e.getMessage());
        }
    }
}
