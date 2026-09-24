package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScVendorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScPublicRegistrationController extends BaseController {

    private final ScVendorService vendorService;

    @GetMapping("/api/public/sc-registration/{token}")
    public Object info(@PathVariable UUID token) {
        try {
            return successResponse(vendorService.getPublicRegistrationInfo(token));
        } catch (Exception e) {
            return failureResponse("Invalid registration link", e.getMessage());
        }
    }

    @PostMapping("/api/public/sc-registration/{token}/complete")
    public Object complete(@PathVariable UUID token, @RequestBody ScPublicRegistrationRequest request) {
        try {
            return successResponse(vendorService.completePublicRegistration(token, request));
        } catch (Exception e) {
            return failureResponse("Registration failed", e.getMessage());
        }
    }
}
