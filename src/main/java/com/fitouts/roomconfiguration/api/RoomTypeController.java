package com.fitouts.roomconfiguration.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fitouts.roomconfiguration.application.RoomTypeService;
import com.fitouts.shared.api.BaseController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/room-types")
@Validated
@RequiredArgsConstructor
public class RoomTypeController extends BaseController {

    private final RoomTypeService roomTypeService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody RoomTypeCreateRequest request) {
        try {
            return successResponse("Room type created successfully", roomTypeService.create(request));
        } catch (Exception e) {
            return failureResponse("Failed to create room type", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @Valid @RequestBody RoomTypeUpdateRequest request) {
        try {
            return successResponse("Room type updated successfully", roomTypeService.update(id, request));
        } catch (Exception e) {
            return failureResponse("Failed to update room type", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        try {
            return successResponse(roomTypeService.getById(id));
        } catch (Exception e) {
            return failureResponse("Failed to fetch room type", e.getMessage());
        }
    }

    @PostMapping("/filter")
    public ResponseEntity<?> list(@RequestBody(required = false) RoomTypeFilterRequest filter,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        try {
            Page<RoomTypeResponse> result = roomTypeService.list(filter, page, size);
            return successResponse(result);
        } catch (Exception e) {
            return failureResponse("Failed to fetch room types", e.getMessage());
        }
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        try {
            return successResponse("Room type activated", roomTypeService.activate(id));
        } catch (Exception e) {
            return failureResponse("Failed to activate room type", e.getMessage());
        }
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        try {
            return successResponse("Room type deactivated", roomTypeService.deactivate(id));
        } catch (Exception e) {
            return failureResponse("Failed to deactivate room type", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> softDelete(@PathVariable UUID id) {
        try {
            roomTypeService.softDelete(id);
            return successResponse("Room type deleted successfully", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete room type", e.getMessage());
        }
    }
}
