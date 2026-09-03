package com.fitouts.schedule.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.schedule.application.ScheduleService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScheduleController extends BaseController {

    private final ScheduleService scheduleService;

    @GetMapping("/api/projects/{projectId}/schedule")
    public Object getSchedule(@PathVariable Long projectId) {
        try {
            return successResponse(scheduleService.getSchedule(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load schedule", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/schedule/activities")
    public Object createActivity(@PathVariable Long projectId, @RequestBody ScheduleActivityRequest request) {
        try {
            return successResponse(scheduleService.createActivity(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create activity", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/schedule/activities/from-room-task")
    public Object createActivityFromRoomTask(
            @PathVariable Long projectId,
            @RequestBody ScheduleFromRoomTaskRequest request) {
        try {
            return successResponse(scheduleService.createActivityFromRoomTask(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to create activity from room task", e.getMessage());
        }
    }

    @PutMapping("/api/schedule/activities/{activityUuid}")
    public Object updateActivity(@PathVariable UUID activityUuid, @RequestBody ScheduleActivityRequest request) {
        try {
            return successResponse(scheduleService.updateActivity(activityUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to update activity", e.getMessage());
        }
    }

    @DeleteMapping("/api/schedule/activities/{activityUuid}")
    public Object deleteActivity(@PathVariable UUID activityUuid) {
        try {
            scheduleService.deleteActivity(activityUuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete activity", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/schedule/dependencies")
    public Object addDependency(@PathVariable Long projectId, @RequestBody ScheduleDependencyRequest request) {
        try {
            return successResponse(scheduleService.addDependency(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to add dependency", e.getMessage());
        }
    }

    @DeleteMapping("/api/schedule/dependencies/{dependencyUuid}")
    public Object deleteDependency(@PathVariable UUID dependencyUuid) {
        try {
            scheduleService.deleteDependency(dependencyUuid);
            return successResponse("Deleted", null);
        } catch (Exception e) {
            return failureResponse("Failed to delete dependency", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/schedule/publish")
    public Object publish(@PathVariable Long projectId) {
        try {
            return successResponse(scheduleService.publish(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to publish schedule", e.getMessage());
        }
    }

    @PostMapping("/api/projects/{projectId}/schedule/baseline")
    public Object createBaseline(@PathVariable Long projectId, @RequestBody(required = false) ScheduleBaselineRequest request) {
        try {
            return successResponse(scheduleService.createBaseline(projectId,
                    request != null ? request : new ScheduleBaselineRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to save baseline", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/schedule/baselines/{baselineUuid}")
    public Object getBaseline(@PathVariable Long projectId, @PathVariable UUID baselineUuid) {
        try {
            return successResponse(scheduleService.getBaseline(projectId, baselineUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load baseline", e.getMessage());
        }
    }

    @PostMapping("/api/schedule/activities/{activityUuid}/progress")
    public Object postProgress(@PathVariable UUID activityUuid, @RequestBody ProgressUpdateRequest request) {
        try {
            return successResponse(scheduleService.postProgress(activityUuid, request));
        } catch (Exception e) {
            return failureResponse("Failed to post progress", e.getMessage());
        }
    }

    @GetMapping("/api/schedule/activities/{activityUuid}/progress")
    public Object listProgress(@PathVariable UUID activityUuid) {
        try {
            return successResponse(scheduleService.listProgress(activityUuid));
        } catch (Exception e) {
            return failureResponse("Failed to list progress", e.getMessage());
        }
    }

    @GetMapping("/api/schedule/calendar-events")
    public Object calendarEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long assigneeAccountId) {
        try {
            return successResponse(scheduleService.calendarEvents(
                    startDate, endDate, projectId, assigneeAccountId));
        } catch (Exception e) {
            return failureResponse("Failed to load calendar events", e.getMessage());
        }
    }

    @GetMapping("/api/schedule/my-activities")
    public Object myActivities() {
        try {
            return successResponse(scheduleService.myActivities());
        } catch (Exception e) {
            return failureResponse("Failed to load my activities", e.getMessage());
        }
    }
}
