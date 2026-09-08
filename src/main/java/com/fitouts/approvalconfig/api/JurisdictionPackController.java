package com.fitouts.approvalconfig.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.approvalconfig.application.JurisdictionPackService;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jurisdiction-packs")
public class JurisdictionPackController extends BaseController {

    private final JurisdictionPackService packService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "false") boolean selectableOnly) {
        try {
            return successResponse(packService.list(selectableOnly));
        } catch (Exception e) {
            return failureResponse("Failed to fetch jurisdiction packs", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        try {
            return successResponse(packService.get(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch jurisdiction pack", e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody JurisdictionPackRequest request) {
        try {
            return successResponse("Jurisdiction pack created", packService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create jurisdiction pack", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody JurisdictionPackRequest request) {
        try {
            return successResponse("Jurisdiction pack updated", packService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update jurisdiction pack", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        try {
            packService.delete(id);
            return successResponse("Jurisdiction pack deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete jurisdiction pack", e.getMessage());
        }
    }
}
