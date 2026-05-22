package com.fitouts.auth.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.auth.application.AuthService;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.api.BaseController;
import com.fitouts.shared.api.MessageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@Validated
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        try {
            AuthService.LoginResult result = authService.login(request, servletRequest, servletResponse);
            return successResponse(result.response());
        } catch (Exception exception) {
            return failureResponse("Unable to login", exception.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        try {
            return successResponse(authService.verifyOtp(request, servletRequest, servletResponse));
        } catch (Exception exception) {
            return failureResponse("Unable to verify OTP", exception.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse,
            @AuthenticationPrincipal AuthPrincipal principal) {

        try {
            authService.logout(servletRequest, servletResponse, principal);
            return successResponse(new MessageResponse("Logged out successfully"));
        } catch (Exception exception) {
            return failureResponse("Unable to logout", exception.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AuthPrincipal principal) {
        try {
            if (principal == null) {
                return successResponse(null);
            }
            return successResponse(authService.me(principal));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch current user", exception.getMessage());
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> sessions(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest servletRequest) {

        try {
            if (principal == null) {
                return successResponse(List.of());
            }
            List<AuthSessionResponse> sessions = authService.getSessions(principal, servletRequest);
            return successResponse(sessions);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch sessions", exception.getMessage());
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> revokeSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String sessionId,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        try {
            if (principal == null) {
                return failureResponse("Unable to revoke session", "Authentication required");
            }
            authService.revokeSession(principal, sessionId, servletRequest, servletResponse);
            return successResponse(new MessageResponse("Session revoked successfully"));
        } catch (Exception exception) {
            return failureResponse("Unable to revoke session", exception.getMessage());
        }
    }
}
