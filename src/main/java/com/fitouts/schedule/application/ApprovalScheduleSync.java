package com.fitouts.schedule.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.approval.domain.ApprovalCase;
import com.fitouts.approval.domain.ApprovalCaseRepository;
import com.fitouts.approval.domain.ApprovalCaseStatus;
import com.fitouts.schedule.domain.ProjectSchedule;
import com.fitouts.schedule.domain.ProjectScheduleRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.engine.WorkingCalendar;

import lombok.RequiredArgsConstructor;

/**
 * Pushes approval reality onto the programme.
 *
 * <p>A permit that is not yet approved is a hard constraint on the activities it blocks: they
 * cannot start until the permit is in hand plus whatever mobilisation the case declares. When
 * the permit lands, the constraint lifts and the network re-solves. When it expires, the
 * constraint comes back.
 *
 * <p>Called after any case status change rather than on a timer, so the Gantt reflects the
 * permit position the moment the PRO records it.
 */
@Component
@RequiredArgsConstructor
public class ApprovalScheduleSync {

    private final ApprovalCaseRepository approvalCaseRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ProjectScheduleRepository projectScheduleRepository;
    private final ScheduleRescheduleService rescheduleService;
    private final WorkCalendarService workCalendarService;

    /**
     * Recomputes constraints for one project and re-solves if anything moved.
     *
     * @return a human-readable note per activity whose constraint changed, for the PM alert.
     */
    @Transactional
    public List<String> syncProject(Long projectId, UUID companyId) {
        List<ScheduleActivity> activities = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(projectId, companyId);
        if (activities.isEmpty()) return List.of();

        Map<String, ScheduleActivity> byCode = new HashMap<>();
        for (ScheduleActivity a : activities) {
            if (a.getActivityCode() != null) byCode.put(a.getActivityCode(), a);
        }
        if (byCode.isEmpty()) return List.of();

        ProjectSchedule schedule = projectScheduleRepository.findByProjectId(projectId).orElse(null);
        WorkingCalendar calendar = workCalendarService.resolve(
                schedule == null ? null : schedule.getWorkCalendarUuid());

        // Clear every permit-imposed constraint first, so an approval genuinely releases the
        // activity rather than leaving a stale hold behind.
        Map<UUID, LocalDate> previous = new HashMap<>();
        for (ScheduleActivity a : activities) {
            if (a.getConstrainedByCaseUuid() != null) {
                previous.put(a.getUuid(), a.getConstraintStartDate());
                a.setConstrainedByCaseUuid(null);
                a.setConstraintStartDate(null);
            }
        }

        LocalDate today = LocalDate.now();
        List<String> notes = new ArrayList<>();
        boolean changed = !previous.isEmpty();

        for (ApprovalCase c : approvalCaseRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtAsc(projectId, companyId)) {
            LocalDate earliestClear = blockingDate(c, today);
            if (earliestClear == null) continue;

            for (String code : splitCodes(c.getBlocksActivityCodes())) {
                ScheduleActivity activity = byCode.get(code);
                if (activity == null) continue;

                LocalDate constraint = calendar.nextWorkingDay(earliestClear);
                LocalDate existing = activity.getConstraintStartDate();
                if (existing != null && existing.isAfter(constraint)) continue;

                activity.setConstrainedByCaseUuid(c.getUuid());
                activity.setConstraintStartDate(constraint);
                changed = true;

                if (!constraint.equals(previous.get(activity.getUuid()))) {
                    notes.add(String.format("%s cannot start before %s: %s is %s.",
                            activity.getName(), constraint, c.getPermitTypeName(),
                            describe(c.getStatus())));
                }
            }
        }

        activityRepository.saveAll(activities);
        if (changed) {
            rescheduleService.reschedule(projectId, null);
        }
        return notes;
    }

    /**
     * The earliest date the activities this case blocks could reasonably start.
     *
     * <p>An approved, unexpired permit imposes nothing. Anything else imposes a hold: either
     * the expected approval date for a case in flight, or tomorrow for one that has not been
     * submitted, because "not started" is not a date anybody can plan around.
     */
    private LocalDate blockingDate(ApprovalCase c, LocalDate today) {
        if (c.getBlocksActivityCodes() == null || c.getBlocksActivityCodes().isBlank()) return null;

        ApprovalCaseStatus status = c.getStatus();
        if (status == ApprovalCaseStatus.WITHDRAWN || status == ApprovalCaseStatus.CLOSED) return null;

        boolean live = status == ApprovalCaseStatus.APPROVED
                || status == ApprovalCaseStatus.ISSUED
                || status == ApprovalCaseStatus.EXPIRING_SOON;
        if (live) {
            // A permit that expires mid-works stops blocking today but will block again on the
            // day it lapses; the renewal case carries that hold, not this one.
            if (c.getExpiryDate() == null || !c.getExpiryDate().isBefore(today)) return null;
        }

        if (status == ApprovalCaseStatus.EXPIRED
                || (live && c.getExpiryDate() != null && c.getExpiryDate().isBefore(today))) {
            // Lapsed permit: the work is stopped until it is renewed, and we do not know when
            // that is, so hold at today rather than pretending it is clear.
            return today;
        }

        if (c.getSlaDueDate() != null && c.getSlaDueDate().isAfter(today)) return c.getSlaDueDate();
        if (c.getTargetSubmissionDate() != null && c.getSlaDays() != null) {
            LocalDate expected = c.getTargetSubmissionDate().plusDays(c.getSlaDays());
            if (expected.isAfter(today)) return expected;
        }
        return today.plusDays(1);
    }

    private String describe(ApprovalCaseStatus status) {
        return switch (status) {
            case NOT_STARTED -> "not yet started";
            case PACK_IN_PREPARATION -> "still being packaged";
            case READY_TO_SUBMIT -> "ready but not submitted";
            case SUBMITTED, UNDER_REVIEW, RESUBMITTED -> "with the authority";
            case COMMENTS_RECEIVED -> "awaiting a response to authority comments";
            case EXPIRED -> "expired";
            case RENEWAL_IN_PROGRESS -> "being renewed";
            case REJECTED -> "rejected";
            default -> status.name().toLowerCase().replace('_', ' ');
        };
    }

    private List<String> splitCodes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> codes = new ArrayList<>();
        for (String part : raw.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) codes.add(trimmed);
        }
        return codes;
    }
}
