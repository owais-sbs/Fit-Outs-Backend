package com.fitouts.platformuser.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.platformuser.application.PlatformUserService;
import com.fitouts.shared.api.BaseController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/platform-users")
@Validated
@RequiredArgsConstructor
public class PlatformUserController extends BaseController {

    private final PlatformUserService service;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "all") String filter,
            @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            List<PlatformUserResponse> users = service.list(filter, principal);
            return successResponse(users);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch platform users", exception.getMessage());
        }
    }

    @PostMapping("/{id}/schedule-deletion")
    public ResponseEntity<?> scheduleDeletion(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            return successResponse(
                    "Account scheduled for deletion",
                    service.scheduleDeletion(id, principal));
        } catch (Exception exception) {
            return failureResponse("Unable to schedule account deletion", exception.getMessage());
        }
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            return successResponse(
                    "Account reactivated successfully",
                    service.reactivate(id, principal));
        } catch (Exception exception) {
            return failureResponse("Unable to reactivate account", exception.getMessage());
        }
    }
}
