package com.fitouts.auth.api;

import java.util.List;

import org.springframework.http.HttpStatus;
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
import com.fitouts.shared.api.MessageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@Validated
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        AuthService.LoginResult result = authService.login(request, servletRequest, servletResponse);
        return ResponseEntity.status(result.pendingOtp() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result.response());
    }

    @PostMapping("/verify-otp")
    public LoginResponse verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        return authService.verifyOtp(request, servletRequest, servletResponse);
    }

    @PostMapping("/logout")
    public MessageResponse logout(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse,
            @AuthenticationPrincipal AuthPrincipal principal) {

        authService.logout(servletRequest, servletResponse, principal);
        return new MessageResponse("Logged out successfully");
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.me(principal);
    }

    @GetMapping("/sessions")
    public List<AuthSessionResponse> sessions(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest servletRequest) {

        return authService.getSessions(principal, servletRequest);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public MessageResponse revokeSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String sessionId,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {

        authService.revokeSession(principal, sessionId, servletRequest, servletResponse);
        return new MessageResponse("Session revoked successfully");
    }
}
