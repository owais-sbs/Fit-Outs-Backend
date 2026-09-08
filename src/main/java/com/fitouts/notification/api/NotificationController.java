package com.fitouts.notification.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.notification.application.NotificationService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController extends BaseController {

    private final NotificationService notificationService;

    @GetMapping
    public Object list(@RequestParam(defaultValue = "50") int limit) {
        try {
            return successResponse(notificationService.list(limit));
        } catch (Exception e) {
            return failureResponse("Failed to load notifications", e.getMessage());
        }
    }

    @GetMapping("/unread-count")
    public Object unreadCount() {
        try {
            return successResponse(Map.of("count", notificationService.unreadCount()));
        } catch (Exception e) {
            return failureResponse("Failed to count notifications", e.getMessage());
        }
    }

    @PostMapping("/read-all")
    public Object markAllRead() {
        try {
            return successResponse(Map.of("updated", notificationService.markAllRead()));
        } catch (Exception e) {
            return failureResponse("Failed to mark notifications read", e.getMessage());
        }
    }

    @PostMapping("/{uuid}/read")
    public Object markRead(@PathVariable UUID uuid) {
        try {
            notificationService.markRead(uuid);
            return successResponse("Marked read", null);
        } catch (Exception e) {
            return failureResponse("Failed to mark notification read", e.getMessage());
        }
    }
}
