package com.fitouts.completion.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.completion.application.CloseoutChecklistService;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.completion.application.FinalAccountService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CloseoutController extends BaseController {

    private final CloseoutChecklistService closeoutChecklistService;
    private final FinalAccountService finalAccountService;
    private final CommercialLifecycleService commercialLifecycleService;

    @GetMapping("/api/projects/{projectId}/completion/close-out")
    public Object get(@PathVariable Long projectId) {
        return successResponse(closeoutChecklistService.get(projectId));
    }

    @GetMapping("/api/projects/{projectId}/completion/final-account")
    public Object getFinalAccount(@PathVariable Long projectId) {
        return successResponse(finalAccountService.get(projectId));
    }

    @PostMapping("/api/projects/{projectId}/completion/close-out/final-invoice")
    public Object confirmFinalInvoice(
            @PathVariable Long projectId,
            @RequestBody(required = false) CloseoutConfirmRequest request) {
        return successResponse(closeoutChecklistService.confirmFinalInvoice(projectId, request));
    }

    @PostMapping("/api/projects/{projectId}/completion/close-out/accounting")
    public Object confirmAccounting(
            @PathVariable Long projectId,
            @RequestBody(required = false) CloseoutConfirmRequest request) {
        return successResponse(closeoutChecklistService.confirmAccounting(projectId, request));
    }

    @PostMapping("/api/projects/{projectId}/completion/close-out/carried-snags")
    public Object carrySnags(
            @PathVariable Long projectId,
            @RequestBody(required = false) CloseoutCarrySnagsRequest request) {
        return successResponse(closeoutChecklistService.carrySnags(projectId, request));
    }

    @PostMapping("/api/projects/{projectId}/completion/commercially-close")
    public Object commerciallyClose(
            @PathVariable Long projectId,
            @RequestBody(required = false) CommerciallyCloseRequest request) {
        return successResponse(commercialLifecycleService.commerciallyClose(projectId, request));
    }

    @PatchMapping("/api/projects/{projectId}/completion/dlp")
    public Object updateDlp(
            @PathVariable Long projectId,
            @RequestBody(required = false) DlpUpdateRequest request) {
        return successResponse(commercialLifecycleService.updateDlp(projectId, request));
    }

    @PostMapping("/api/projects/{projectId}/completion/archive")
    public Object archive(@PathVariable Long projectId) {
        return successResponse(commercialLifecycleService.archive(projectId));
    }
}
