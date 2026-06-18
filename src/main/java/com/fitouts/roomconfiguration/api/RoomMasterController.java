package com.fitouts.roomconfiguration.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
// import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fitouts.roomconfiguration.application.RoomMasterService;
import com.fitouts.shared.api.BaseController;

// import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/room-masters")
// @Validated
@RequiredArgsConstructor
public class RoomMasterController extends BaseController {

    private final RoomMasterService roomMasterService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RoomMasterCreateRequest request) {
        try {
            return successResponse("Room master created successfully", roomMasterService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create room master", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody RoomMasterUpdateRequest request) {
        try {
            return successResponse("Room master updated successfully", roomMasterService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update room master", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return successResponse(roomMasterService.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch room master", e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return successResponse(roomMasterService.list());
        } catch (Exception e) {
            return failureResponse("Failed to fetch room masters", e.getMessage());
        }
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        try {
            return successResponse("Room master activated", roomMasterService.activate(id));
        } catch (Exception e) {
            return failureResponse("Failed to activate room master", e.getMessage());
        }
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        try {
            return successResponse("Room master deactivated", roomMasterService.deactivate(id));
        } catch (Exception e) {
            return failureResponse("Failed to deactivate room master", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> softDelete(@PathVariable UUID id) {
        try {
            roomMasterService.softDelete(id);
            return successResponse("Room master deleted successfully", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete room master", e.getMessage());
        }
    }
}
