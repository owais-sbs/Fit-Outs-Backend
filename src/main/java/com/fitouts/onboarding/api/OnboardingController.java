package com.fitouts.onboarding.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.account.application.AccountService;
import com.fitouts.account.domain.Account;
import com.fitouts.auth.application.AuthService;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.onboarding.application.OnboardingService;
import com.fitouts.shared.api.BaseController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/onboarding")
@Validated
@RequiredArgsConstructor
public class OnboardingController extends BaseController {

    private final OnboardingService onboardingService;
    private final AuthService authService;
    private final AccountService accountService;

    @GetMapping("/payment-instructions")
    public ResponseEntity<?> paymentInstructions() {
        try {
            return successResponse(onboardingService.paymentInstructions());
        } catch (Exception exception) {
            return failureResponse("Unable to load payment instructions", exception.getMessage());
        }
    }

    @PostMapping("/submit-payment")
    public ResponseEntity<?> submitPayment(
            @Valid @RequestBody SubmitPaymentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            var response = onboardingService.submitPayment(principal, request);
            // Rebind session so CompanyContext picks up the newly linked company.
            Account account = accountService.getAccountByEmail(principal.getEmail());
            authService.refreshSession(account, servletRequest, servletResponse);
            return successResponse(response);
        } catch (Exception exception) {
            return failureResponse("Unable to submit payment", exception.getMessage());
        }
    }

    @PostMapping(value = "/company-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> completeCompanyProfile(
            @RequestPart("companyName") String companyName,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "stamp", required = false) MultipartFile stamp,
            @RequestPart(value = "signature", required = false) MultipartFile signature,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            var response = onboardingService.completeCompanyProfile(
                    principal, companyName, logo, stamp, signature);
            Account account = accountService.getAccountByEmail(principal.getEmail());
            authService.refreshSession(account, servletRequest, servletResponse);
            return successResponse(response);
        } catch (Exception exception) {
            return failureResponse("Unable to save company profile", exception.getMessage());
        }
    }
}
