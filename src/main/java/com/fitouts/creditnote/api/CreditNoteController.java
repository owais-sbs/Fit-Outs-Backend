package com.fitouts.creditnote.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.creditnote.application.CreditNoteService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CreditNoteController extends BaseController {

    private final CreditNoteService creditNoteService;

    @GetMapping("/api/projects/{projectId}/credit-notes")
    public Object list(@PathVariable Long projectId) {
        try {
            return successResponse(creditNoteService.list(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list credit notes", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/credit-notes")
    public Object create(@PathVariable Long projectId, @RequestBody CreditNoteRequest request) {
        try {
            return successResponse(creditNoteService.create(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create credit note", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/credit-notes/{uuid}/submit")
    public Object submit(@PathVariable Long projectId, @PathVariable UUID uuid) {
        try {
            return successResponse(creditNoteService.submit(projectId, uuid));
        } catch (Exception e) {
            return failureResponse("Failed to submit credit note", e.getMessage());
        }
    }
}
