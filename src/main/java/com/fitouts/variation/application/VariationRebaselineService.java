package com.fitouts.variation.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.notification.application.NotificationService;
import com.fitouts.schedule.api.RescheduleRequest;
import com.fitouts.schedule.api.ScheduleBaselineResponse;
import com.fitouts.schedule.application.ScheduleRescheduleService;
import com.fitouts.schedule.application.ScheduleService;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.variation.domain.VariationEvent;
import com.fitouts.variation.domain.VariationEventRepository;
import com.fitouts.variation.domain.VariationLink;
import com.fitouts.variation.domain.VariationLinkRepository;
import com.fitouts.variation.domain.VariationLinkType;
import com.fitouts.variation.domain.VariationRequest;
import com.fitouts.variation.domain.VariationRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VariationRebaselineService {

    public record RebaselineResult(
            String status,
            String detailJson,
            UUID baselineUuid,
            String baselineName,
            List<Map<String, Object>> activities,
            String error) {
    }

    private final VariationRequestRepository variationRepository;
    private final VariationLinkRepository linkRepository;
    private final VariationEventRepository eventRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ScheduleRescheduleService scheduleRescheduleService;
    private final ScheduleService scheduleService;
    private final NotificationService notificationService;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RebaselineResult rebaseline(Long projectId, VariationRequest vr, Long actorAccountId) {
        if (vr == null) {
            return new RebaselineResult("SKIPPED", "{\"status\":\"SKIPPED\",\"message\":\"Variation is null\"}", null, null, List.of(), null);
        }

        boolean applyFlag = vr.isApplyScheduleOnApproval();
        if (!applyFlag) {
            log.info("Schedule re-baseline skipped for CR {}: applyScheduleOnApproval is false", vr.getCrNumber());
            String detailJson = toJson(Map.of(
                    "status", "SKIPPED",
                    "message", "Locked after client approval — schedule rebaseline skipped (apply flag not set)"
            ));
            return new RebaselineResult("SKIPPED", detailJson, null, null, List.of(), null);
        }

        try {
            List<VariationLink> links = linkRepository.findByVariationUuid(vr.getUuid());
            List<VariationLink> activityLinks = links != null ? links.stream()
                    .filter(l -> l.getLinkType() == VariationLinkType.ACTIVITY && l.getActivityUuid() != null)
                    .toList() : List.of();

            Integer delayDays = vr.getProposedDelayDays();
            boolean hasDelay = delayDays != null && delayDays != 0;
            boolean shouldExtend = hasDelay && !activityLinks.isEmpty();

            List<Map<String, Object>> activityDiffs = new ArrayList<>();
            UUID companyId = vr.getCompanyId() != null ? vr.getCompanyId() : CompanyContext.get();

            if (shouldExtend) {
                for (VariationLink link : activityLinks) {
                    ScheduleActivity activity = activityRepository.findByUuidAndCompanyId(link.getActivityUuid(), companyId)
                            .orElse(null);
                    if (activity == null) {
                        continue;
                    }
                    int oldDuration = activity.getDurationWorkingDays() != null ? activity.getDurationWorkingDays() : 1;
                    int newDuration = Math.max(1, oldDuration + delayDays);

                    RescheduleRequest rescheduleRequest = new RescheduleRequest();
                    rescheduleRequest.setActivityUuid(activity.getUuid());
                    rescheduleRequest.setDurationWorkingDays(newDuration);
                    scheduleRescheduleService.reschedule(projectId, rescheduleRequest);

                    Map<String, Object> diff = new LinkedHashMap<>();
                    diff.put("activityUuid", activity.getUuid().toString());
                    diff.put("activityName", activity.getName());
                    diff.put("oldDuration", oldDuration);
                    diff.put("newDuration", newDuration);
                    diff.put("delayApplied", delayDays);
                    activityDiffs.add(diff);
                }
            }

            String baselineName = vr.getCrNumber() + " approved";
            ScheduleBaselineResponse baseline = scheduleService.createBaselineForSystem(projectId, baselineName, actorAccountId);

            Map<String, Object> detailMap = new LinkedHashMap<>();
            detailMap.put("status", "COMPLETED");
            detailMap.put("message", shouldExtend
                    ? "Locked after client approval — schedule re-baselined: extended " + activityDiffs.size() + " activities and created baseline " + baselineName
                    : "Locked after client approval — schedule re-baselined: created baseline " + baselineName);
            detailMap.put("baselineUuid", baseline.getUuid() != null ? baseline.getUuid().toString() : null);
            detailMap.put("baselineName", baseline.getName());
            detailMap.put("activities", activityDiffs);

            String detailJson = toJson(detailMap);
            log.info("Schedule re-baseline completed for CR {}: baselineUuid={}", vr.getCrNumber(), baseline.getUuid());

            return new RebaselineResult("COMPLETED", detailJson, baseline.getUuid(), baseline.getName(), activityDiffs, null);

        } catch (Exception ex) {
            log.error("Failed to re-baseline schedule for CR {}: {}", vr.getCrNumber(), ex.getMessage(), ex);

            Map<String, Object> failureMap = new LinkedHashMap<>();
            failureMap.put("status", "FAILED");
            failureMap.put("message", "Locked after client approval — schedule re-baseline failed");
            failureMap.put("error", ex.getMessage() != null ? ex.getMessage() : "Unknown schedule re-baseline error");
            String failureJson = toJson(failureMap);

            // Record a distinct SCHEDULE_REBASELINE_FAILED event
            VariationEvent failedEvent = new VariationEvent();
            failedEvent.setVariationUuid(vr.getUuid());
            failedEvent.setAction("SCHEDULE_REBASELINE_FAILED");
            failedEvent.setFromStatus(vr.getStatus() != null ? vr.getStatus().name() : null);
            failedEvent.setToStatus("APPROVED");
            failedEvent.setActorId(actorAccountId);
            failedEvent.setDetail(failureJson);
            eventRepository.save(failedEvent);

            // Alert staff
            notifyStaffFailure(vr, actorAccountId, ex.getMessage());

            return new RebaselineResult("FAILED", failureJson, null, null, List.of(), ex.getMessage());
        }
    }

    private void notifyStaffFailure(VariationRequest vr, Long actorAccountId, String errorMessage) {
        if (vr == null || vr.getCompanyId() == null) return;
        try {
            if (vr.getSubmittedBy() != null) {
                raiseAlert(vr, vr.getSubmittedBy(), "SCHEDULE_REBASELINE_FAILED", "WARNING",
                        "Schedule re-baseline failed: " + vr.getCrNumber(),
                        "Client approved CR but schedule re-baseline failed: " + errorMessage,
                        "/admin/projects/" + vr.getProjectId() + "/variations/" + vr.getUuid(),
                        "vr-rebaseline-fail:" + vr.getUuid() + ":" + vr.getSubmittedBy());
            }
            for (Account pm : accountRepository.findAllByCompanyUuidAndRole(vr.getCompanyId(), Role.PROJECT_MANAGER)) {
                raiseAlert(vr, pm.getId(), "SCHEDULE_REBASELINE_FAILED", "WARNING",
                        "Schedule re-baseline failed: " + vr.getCrNumber(),
                        "Client approved CR but schedule re-baseline failed: " + errorMessage,
                        "/admin/projects/" + vr.getProjectId() + "/variations/" + vr.getUuid(),
                        "vr-rebaseline-fail:" + vr.getUuid() + ":" + pm.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to notify staff of schedule rebaseline failure: {}", e.getMessage());
        }
    }

    private void raiseAlert(VariationRequest vr, Long accountId, String category, String severity,
                            String title, String body, String link, String dedupeKey) {
        notificationService.raise(new NotificationService.Alert(
                vr.getCompanyId(), accountId, category, severity, title, body, link,
                "VARIATION", vr.getUuid(), dedupeKey, false));
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }
}
