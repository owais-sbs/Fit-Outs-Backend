package com.fitouts.subcontractor.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;
import com.fitouts.subcontractor.application.ScVendorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subcontractors")
@RequiredArgsConstructor
public class ScVendorController extends BaseController {

    private final ScVendorService vendorService;

    /** Invite-only onboarding — staff sends portal invite; no open public registration. */
    @PostMapping("/invite")
    public Object invite(@RequestBody ScInviteVendorRequest request) {
        try {
            return successResponse(vendorService.inviteVendor(request));
        } catch (Exception e) {
            return failureResponse("Failed to invite subcontractor", e.getMessage());
        }
    }

    @GetMapping
    public Object list(@RequestParam(required = false) String status) {
        try {
            return successResponse(vendorService.listVendors(status));
        } catch (Exception e) {
            return failureResponse("Failed to list subcontractors", e.getMessage());
        }
    }

    @GetMapping("/{organizationUuid}")
    public Object get(@PathVariable UUID organizationUuid) {
        try {
            return successResponse(vendorService.getVendor(organizationUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load subcontractor", e.getMessage());
        }
    }

    /** QS or PM prequalification review. */
    @PutMapping("/{organizationUuid}/prequalification")
    public Object reviewPrequalification(
            @PathVariable UUID organizationUuid,
            @RequestBody ScPrequalificationReviewRequest request) {
        try {
            return successResponse(vendorService.reviewPrequalification(organizationUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to save prequalification review", e.getMessage());
        }
    }
}
