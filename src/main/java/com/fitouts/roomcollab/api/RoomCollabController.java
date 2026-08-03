package com.fitouts.roomcollab.api;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fitouts.roomcollab.application.RoomCollabService;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class RoomCollabController extends BaseController {

    private final RoomCollabService roomCollabService;

    // --- Rooms ---

    @GetMapping("/rooms")
    public ResponseEntity<?> listRooms(@PathVariable Long projectId) {
        try {
            return successResponse(roomCollabService.listRooms(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list rooms", e.getMessage());
        }
    }

    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(@PathVariable Long projectId, @RequestBody ProjectRoomCreateRequest request) {
        try {
            return successResponse("Room created", roomCollabService.createRoom(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create room", e.getMessage());
        }
    }

    @PatchMapping("/rooms/{roomId}")
    public ResponseEntity<?> updateRoom(
            @PathVariable Long projectId,
            @PathVariable UUID roomId,
            @RequestBody ProjectRoomCreateRequest request) {
        try {
            return successResponse(roomCollabService.updateRoom(projectId, roomId, request));
        } catch (Exception e) {
            return failureResponse("Failed to update room", e.getMessage());
        }
    }

    @PostMapping("/rooms/sync-from-boq")
    public ResponseEntity<?> syncFromBoq(@PathVariable Long projectId) {
        try {
            int created = roomCollabService.syncRoomsFromBoq(projectId);
            return successResponse("Synced " + created + " room(s)", created);
        } catch (Exception e) {
            return failureResponse("Failed to sync rooms from BOQ", e.getMessage());
        }
    }

    @GetMapping("/rooms/{roomId}/tasks")
    public ResponseEntity<?> listRoomTasks(@PathVariable Long projectId, @PathVariable UUID roomId) {
        try {
            return successResponse(roomCollabService.listTasksForRoom(projectId, roomId));
        } catch (Exception e) {
            return failureResponse("Failed to list tasks", e.getMessage());
        }
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> listRoomMessages(@PathVariable Long projectId, @PathVariable UUID roomId) {
        try {
            return successResponse(roomCollabService.listRoomMessages(projectId, roomId));
        } catch (Exception e) {
            return failureResponse("Failed to list room messages", e.getMessage());
        }
    }

    @PostMapping(value = "/rooms/{roomId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> postRoomMessage(
            @PathVariable Long projectId,
            @PathVariable UUID roomId,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "linkedTaskId", required = false) UUID linkedTaskId,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            return successResponse(roomCollabService.postRoomMessage(projectId, roomId, body, linkedTaskId, file));
        } catch (Exception e) {
            return failureResponse("Failed to post message", e.getMessage());
        }
    }

    // --- Tasks ---

    @GetMapping("/room-tasks")
    public ResponseEntity<?> listProjectTasks(@PathVariable Long projectId) {
        try {
            return successResponse(roomCollabService.listTasksForProject(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list tasks", e.getMessage());
        }
    }

    @GetMapping("/room-tasks/pending-client")
    public ResponseEntity<?> pendingClient(@PathVariable Long projectId) {
        try {
            return successResponse(roomCollabService.listPendingClientTasks(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to list pending tasks", e.getMessage());
        }
    }

    @PostMapping("/room-tasks")
    public ResponseEntity<?> createTask(@PathVariable Long projectId, @RequestBody RoomTaskCreateRequest request) {
        try {
            return successResponse("Task created", roomCollabService.createTask(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create task", e.getMessage());
        }
    }

    @GetMapping("/room-tasks/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable Long projectId, @PathVariable UUID taskId) {
        try {
            return successResponse(roomCollabService.getTask(projectId, taskId));
        } catch (Exception e) {
            return failureResponse("Failed to fetch task", e.getMessage());
        }
    }

    @PatchMapping("/room-tasks/{taskId}")
    public ResponseEntity<?> updateTask(
            @PathVariable Long projectId,
            @PathVariable UUID taskId,
            @RequestBody RoomTaskUpdateRequest request) {
        try {
            return successResponse(roomCollabService.updateTask(projectId, taskId, request));
        } catch (Exception e) {
            return failureResponse("Failed to update task", e.getMessage());
        }
    }

    @PostMapping(value = "/room-tasks/{taskId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadVersion(
            @PathVariable Long projectId,
            @PathVariable UUID taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "changeNotes", required = false) String changeNotes) {
        try {
            return successResponse("Version uploaded",
                    roomCollabService.uploadVersion(projectId, taskId, file, changeNotes));
        } catch (Exception e) {
            return failureResponse("Failed to upload version", e.getMessage());
        }
    }

    @GetMapping("/room-tasks/{taskId}/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable Long projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID versionId) {
        Resource resource = roomCollabService.loadVersionResource(projectId, taskId, versionId);
        MediaType mediaType = roomCollabService.versionMediaType(projectId, taskId, versionId);
        String filename = roomCollabService.versionFileName(projectId, taskId, versionId);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping("/room-tasks/{taskId}/submit")
    public ResponseEntity<?> submit(@PathVariable Long projectId, @PathVariable UUID taskId) {
        try {
            return successResponse(roomCollabService.submitToClient(projectId, taskId));
        } catch (Exception e) {
            return failureResponse("Failed to submit to client", e.getMessage());
        }
    }

    @PostMapping("/room-tasks/{taskId}/request-changes")
    public ResponseEntity<?> requestChanges(
            @PathVariable Long projectId,
            @PathVariable UUID taskId,
            @RequestBody(required = false) ChangeRequestBody body) {
        try {
            return successResponse(roomCollabService.requestChanges(projectId, taskId, body));
        } catch (Exception e) {
            return failureResponse("Failed to request changes", e.getMessage());
        }
    }

    @PostMapping("/room-tasks/{taskId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long projectId, @PathVariable UUID taskId) {
        try {
            return successResponse(roomCollabService.approve(projectId, taskId));
        } catch (Exception e) {
            return failureResponse("Failed to approve task", e.getMessage());
        }
    }

    @PostMapping("/room-tasks/{taskId}/close")
    public ResponseEntity<?> close(@PathVariable Long projectId, @PathVariable UUID taskId) {
        try {
            return successResponse(roomCollabService.closeTask(projectId, taskId));
        } catch (Exception e) {
            return failureResponse("Failed to close task", e.getMessage());
        }
    }

    @GetMapping("/room-tasks/{taskId}/timeline")
    public ResponseEntity<?> timeline(@PathVariable Long projectId, @PathVariable UUID taskId) {
        try {
            return successResponse(roomCollabService.timeline(projectId, taskId));
        } catch (Exception e) {
            return failureResponse("Failed to load timeline", e.getMessage());
        }
    }

    @GetMapping("/room-tasks/{taskId}/messages")
    public ResponseEntity<?> taskMessages(@PathVariable Long projectId, @PathVariable UUID taskId) {
        try {
            return successResponse(roomCollabService.listTaskMessages(projectId, taskId));
        } catch (Exception e) {
            return failureResponse("Failed to list messages", e.getMessage());
        }
    }

    @PostMapping(value = "/room-tasks/{taskId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> postTaskMessage(
            @PathVariable Long projectId,
            @PathVariable UUID taskId,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "referencedVersionId", required = false) UUID referencedVersionId,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            return successResponse(roomCollabService.postTaskMessage(
                    projectId, taskId, body, file, referencedVersionId));
        } catch (Exception e) {
            return failureResponse("Failed to post message", e.getMessage());
        }
    }

    @GetMapping("/final-report")
    public ResponseEntity<?> finalReport(@PathVariable Long projectId) {
        try {
            return successResponse(roomCollabService.finalReport(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to build final report", e.getMessage());
        }
    }
}
