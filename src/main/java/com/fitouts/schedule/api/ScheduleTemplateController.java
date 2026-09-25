package com.fitouts.schedule.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.schedule.application.ScheduleRescheduleService;
import com.fitouts.schedule.application.ScheduleTemplateService;
import com.fitouts.schedule.application.BoqScheduleImportService;
import com.fitouts.schedule.application.WorkCalendarService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

/** Template library, CPM preview/apply, order-by dates and live rescheduling. */
@RestController
@RequiredArgsConstructor
public class ScheduleTemplateController extends BaseController {

    private final ScheduleTemplateService templateService;
    private final ScheduleRescheduleService rescheduleService;
    private final WorkCalendarService workCalendarService;
    private final BoqScheduleImportService boqScheduleImportService;

    @GetMapping("/api/schedule/templates")
    public Object listTemplates() {
        try {
            return successResponse(templateService.listTemplates());
        } catch (Exception e) {
            return failureResponse("Failed to load schedule templates", e.getMessage());
        }
    }

    @GetMapping("/api/schedule/templates/{templateUuid}")
    public Object getTemplate(@PathVariable UUID templateUuid) {
        try {
            return successResponse(templateService.getTemplate(templateUuid));
        } catch (Exception e) {
            return failureResponse("Failed to load template", e.getMessage());
        }
    }

    @GetMapping("/api/schedule/work-calendars")
    public Object listCalendars() {
        try {
            return successResponse(workCalendarService.list());
        } catch (Exception e) {
            return failureResponse("Failed to load work calendars", e.getMessage());
        }
    }

    /** Solves the template against these parameters without writing anything. */
    @PostMapping("/api/projects/{projectId}/schedule/preview")
    public Object preview(@PathVariable Long projectId, @RequestBody SchedulePreviewRequest request) {
        try {
            return successResponse(templateService.preview(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to preview schedule", e.getMessage());
        }
    }

    /** Writes the programme and runs the downstream cascade. */
    @PostMapping("/api/projects/{projectId}/schedule/apply")
    public Object apply(@PathVariable Long projectId, @RequestBody SchedulePreviewRequest request) {
        try {
            return successResponse(templateService.apply(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to apply schedule", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/schedule/order-by-dates")
    public Object orderByDates(@PathVariable Long projectId) {
        try {
            return successResponse(templateService.orderByDates(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load order-by dates", e.getMessage());
        }
    }

    /** Re-runs CPM after a bar drag, duration change, or a permit clearing. */
    @PostMapping("/api/projects/{projectId}/schedule/reschedule")
    public Object reschedule(@PathVariable Long projectId,
                             @RequestBody(required = false) RescheduleRequest request) {
        try {
            return successResponse(rescheduleService.reschedule(
                    projectId, request != null ? request : new RescheduleRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to reschedule", e.getMessage());
        }
    }

    /** Copies the live programme into a new tenant template. */
    @PostMapping("/api/projects/{projectId}/schedule/save-as-template")
    public Object saveAsTemplate(@PathVariable Long projectId,
                                 @RequestBody(required = false) SaveAsTemplateRequest request) {
        try {
            return successResponse(templateService.saveProjectAsTemplate(
                    projectId, request != null ? request : new SaveAsTemplateRequest()));
        } catch (Exception e) {
            return failureResponse("Failed to save schedule as template", e.getMessage());
        }
    }

    /** Mode 3: suggest template activity matches for approved BOQ lines. */
    @PostMapping("/api/projects/{projectId}/schedule/programme/suggest-boq-matches")
    public Object suggestBoqMatches(@PathVariable Long projectId,
                                    @RequestBody SuggestBoqMatchesRequest request) {
        try {
            return successResponse(boqScheduleImportService.suggestMatches(
                    projectId, request != null ? request.getTemplateUuid() : null));
        } catch (Exception e) {
            return failureResponse("Failed to suggest BOQ matches", e.getMessage());
        }
    }

    /** Mode 2 (BOQ) / Mode 3 (BLEND) programme preview. */
    @PostMapping("/api/projects/{projectId}/schedule/programme/preview")
    public Object previewProgramme(@PathVariable Long projectId,
                                   @RequestBody ProgrammeBuildRequest request) {
        try {
            return successResponse(boqScheduleImportService.preview(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to preview programme", e.getMessage());
        }
    }

    /** Mode 2 (BOQ) / Mode 3 (BLEND) programme apply (replaces current schedule). */
    @PostMapping("/api/projects/{projectId}/schedule/programme/apply")
    public Object applyProgramme(@PathVariable Long projectId,
                                 @RequestBody ProgrammeBuildRequest request) {
        try {
            return successResponse(boqScheduleImportService.apply(projectId, request));
        } catch (Exception e) {
            return failureResponse("Failed to apply programme", e.getMessage());
        }
    }
}
