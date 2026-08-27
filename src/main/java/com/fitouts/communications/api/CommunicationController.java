package com.fitouts.communications.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.communications.application.CommunicationService;
import com.fitouts.communications.dto.CreateChannelRequest;
import com.fitouts.communications.dto.SendChannelMessageRequest;
import com.fitouts.shared.api.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/communications")
@RequiredArgsConstructor
public class CommunicationController extends BaseController {

    private final CommunicationService communicationService;

    @GetMapping("/inbox")
    public ResponseEntity<?> inbox(@RequestParam(required = false) String filter) {
        try {
            return successResponse(communicationService.getInbox(filter));
        } catch (Exception e) {
            return failureResponse("Unable to load inbox", e.getMessage());
        }
    }

    @GetMapping("/channels/{uuid}/messages")
    public ResponseEntity<?> messages(@PathVariable UUID uuid) {
        try {
            return successResponse(communicationService.getMessages(uuid));
        } catch (Exception e) {
            return failureResponse("Unable to load messages", e.getMessage());
        }
    }

    @PostMapping("/channels/{uuid}/messages")
    public ResponseEntity<?> send(
            @PathVariable UUID uuid,
            @RequestBody SendChannelMessageRequest request) {
        try {
            return successResponse(
                    "Message sent",
                    communicationService.sendMessage(uuid, request.getBody()));
        } catch (Exception e) {
            return failureResponse("Unable to send message", e.getMessage());
        }
    }

    @PostMapping("/channels")
    public ResponseEntity<?> create(@RequestBody CreateChannelRequest request) {
        try {
            return successResponse("Channel created", communicationService.createChannel(request));
        } catch (Exception e) {
            return failureResponse("Unable to create channel", e.getMessage());
        }
    }

    @PostMapping("/sync-channels")
    public ResponseEntity<?> syncChannels() {
        try {
            communicationService.syncProjectChannels();
            return successResponse("Channels synced", null);
        } catch (Exception e) {
            return failureResponse("Unable to sync channels", e.getMessage());
        }
    }

    @GetMapping("/channels/resolve")
    public ResponseEntity<?> resolveChannel(
            @RequestParam(required = false) UUID projectRoomId,
            @RequestParam(required = false) UUID roomTaskId) {
        try {
            return successResponse(communicationService.resolveChannelUuid(projectRoomId, roomTaskId));
        } catch (Exception e) {
            return failureResponse("Unable to resolve channel", e.getMessage());
        }
    }

    @PatchMapping("/channels/{uuid}/read")
    public ResponseEntity<?> markRead(@PathVariable UUID uuid) {
        try {
            communicationService.markRead(uuid);
            return successResponse("Marked read", null);
        } catch (Exception e) {
            return failureResponse("Unable to mark read", e.getMessage());
        }
    }
}
