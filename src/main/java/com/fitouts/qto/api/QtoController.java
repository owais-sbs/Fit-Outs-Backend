package com.fitouts.qto.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fitouts.qto.application.QtoService;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/qto")
@RequiredArgsConstructor
public class QtoController extends BaseController {

    private final QtoService qtoService;

    @PostMapping("/sessions")
    public ResponseEntity<?> create(@RequestBody QtoSessionCreateRequest request) {
        try {
            return successResponse("QTO session created", qtoService.createSession(request));
        } catch (Exception e) {
            return failureResponse("Failed to create QTO session", e.getMessage());
        }
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        try {
            return successResponse(qtoService.getSession(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch QTO session", e.getMessage());
        }
    }

    @GetMapping("/sessions/project/{projectId}")
    public ResponseEntity<?> listByProject(@PathVariable Long projectId) {
        try {
            return successResponse(qtoService.listByProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list QTO sessions", e.getMessage());
        }
    }

    @PatchMapping("/sessions/{id}/scale")
    public ResponseEntity<?> updateScale(@PathVariable UUID id, @RequestBody QtoScaleRequest request) {
        try {
            return successResponse("Scale updated", qtoService.updateScale(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update scale", e.getMessage());
        }
    }

    @PutMapping("/sessions/{id}/lines")
    public ResponseEntity<?> updateLines(@PathVariable UUID id, @RequestBody QtoLinesUpdateRequest request) {
        try {
            return successResponse("Lines updated", qtoService.replaceLines(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update lines", e.getMessage());
        }
    }

    @PostMapping("/sessions/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID id) {
        try {
            return successResponse("QTO approved", qtoService.approve(id));
        } catch (Exception e) {
            return failureResponse("Failed to approve QTO", e.getMessage());
        }
    }
}
