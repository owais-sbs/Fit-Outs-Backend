package com.fitouts.resource.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.resource.application.LabourCrewService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/labour-crews")
@RequiredArgsConstructor
public class LabourCrewController extends BaseController {

    private final LabourCrewService labourCrewService;

    @GetMapping
    public Object list() {
        try {
            return successResponse(labourCrewService.list());
        } catch (Exception e) {
            return failureResponse("Failed to list labour crews", e.getMessage());
        }
    }

    @PostMapping
    public Object create(@RequestBody LabourCrewRequest request) {
        try {
            return successResponse(labourCrewService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create labour crew", e.getMessage());
        }
    }

    @PutMapping("/{uuid}")
    public Object update(@PathVariable UUID uuid, @RequestBody LabourCrewRequest request) {
        try {
            return successResponse(labourCrewService.update(uuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update labour crew", e.getMessage());
        }
    }

    @DeleteMapping("/{uuid}")
    public Object delete(@PathVariable UUID uuid) {
        try {
            labourCrewService.delete(uuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete labour crew", e.getMessage());
        }
    }
}
