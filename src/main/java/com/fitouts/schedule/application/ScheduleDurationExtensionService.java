package com.fitouts.schedule.application;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.completion.application.CommercialLifecycleService;
import com.fitouts.project.domain.Project;
import com.fitouts.project.domain.ProjectRepository;
import com.fitouts.project.domain.ProjectTeamAssignmentRepository;
import com.fitouts.project.domain.ProjectTeamRole;
import com.fitouts.schedule.api.DurationExtensionCreateRequest;
import com.fitouts.schedule.api.DurationExtensionDecisionRequest;
import com.fitouts.schedule.api.DurationExtensionResponse;
import com.fitouts.schedule.api.RescheduleRequest;
import com.fitouts.schedule.domain.DelayReasonCode;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.domain.ScheduleDurationExtension;
import com.fitouts.schedule.domain.ScheduleDurationExtensionRepository;
import com.fitouts.schedule.domain.ScheduleDurationExtensionStatus;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleDurationExtensionService {

    private final ScheduleDurationExtensionRepository extensionRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ProjectTeamAssignmentRepository teamAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final ScheduleRescheduleService rescheduleService;
    private final CommercialLifecycleService commercialLifecycleService;

    @Transactional
    public DurationExtensionResponse create(UUID activityUuid, DurationExtensionCreateRequest request) {
        AuthPrincipal principal = requireAuthenticated();
        UUID companyId = requireCompany();
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        ScheduleActivity activity = activityRepository.findByUuidAndCompanyId(activityUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        commercialLifecycleService.assertNotArchived(activity.getProjectId());

        if (!isTeamSiteEngineer(principal, activity.getProjectId())) {
            throw new ForbiddenException("Only the project Site Engineer can request a duration extension");
        }

        Integer current = activity.getDurationWorkingDays() != null ? activity.getDurationWorkingDays() : 0;
        Integer requested = request.getRequestedDurationWorkingDays();
        if (requested == null || requested <= current) {
            throw new BadRequestException("Requested duration must be greater than the current duration (" + current + " working days)");
        }
        if (current < 1) {
            throw new BadRequestException("Activity has no valid current duration");
        }

        DelayReasonCode code = request.getDelayReasonCode();
        if (code == null) {
            throw new BadRequestException("delayReasonCode is required");
        }
        String reasonText = StringUtils.hasText(request.getDelayReasonText())
                ? request.getDelayReasonText().trim()
                : null;
        if (code == DelayReasonCode.OTHER && !StringUtils.hasText(reasonText)) {
            throw new BadRequestException("delayReasonText is required when delayReasonCode is OTHER");
        }

        if (extensionRepository.existsByActivityUuidAndStatus(activityUuid, ScheduleDurationExtensionStatus.PENDING)) {
            throw new BadRequestException("A pending duration extension already exists for this activity");
        }

        ScheduleDurationExtension ext = new ScheduleDurationExtension();
        ext.setCompanyId(companyId);
        ext.setProjectId(activity.getProjectId());
        ext.setActivityUuid(activity.getUuid());
        ext.setRequestedBy(principal.getAccountId());
        ext.setCurrentDurationWorkingDays(current);
        ext.setRequestedDurationWorkingDays(requested);
        ext.setDelayReasonCode(code);
        ext.setDelayReasonText(reasonText);
        ext.setStatus(ScheduleDurationExtensionStatus.PENDING);

        return toResponse(extensionRepository.save(ext), activity, null, null);
    }

    @Transactional(readOnly = true)
    public List<DurationExtensionResponse> inbox() {
        requirePmOrAdmin();
        UUID companyId = requireCompany();
        List<ScheduleDurationExtension> pending = extensionRepository
                .findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, ScheduleDurationExtensionStatus.PENDING);
        return enrichList(pending);
    }

    @Transactional
    public DurationExtensionResponse approve(UUID uuid, DurationExtensionDecisionRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        ScheduleDurationExtension ext = requirePending(uuid);
        commercialLifecycleService.assertNotArchived(ext.getProjectId());

        RescheduleRequest rescheduleRequest = new RescheduleRequest();
        rescheduleRequest.setActivityUuid(ext.getActivityUuid());
        rescheduleRequest.setDurationWorkingDays(ext.getRequestedDurationWorkingDays());
        rescheduleService.reschedule(ext.getProjectId(), rescheduleRequest);

        ScheduleActivity activity = activityRepository
                .findByUuidAndCompanyId(ext.getActivityUuid(), requireCompany())
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        activity.setDelayReason(formatDelayReason(ext));
        activityRepository.save(activity);

        ext.setStatus(ScheduleDurationExtensionStatus.APPROVED);
        ext.setDecidedBy(principal.getAccountId());
        ext.setDecidedAt(OffsetDateTime.now());
        if (request != null && StringUtils.hasText(request.getDecisionNotes())) {
            ext.setDecisionNotes(request.getDecisionNotes().trim());
        }
        return toResponse(extensionRepository.save(ext), activity, null, null);
    }

    @Transactional
    public DurationExtensionResponse reject(UUID uuid, DurationExtensionDecisionRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        ScheduleDurationExtension ext = requirePending(uuid);
        commercialLifecycleService.assertNotArchived(ext.getProjectId());

        ext.setStatus(ScheduleDurationExtensionStatus.REJECTED);
        ext.setDecidedBy(principal.getAccountId());
        ext.setDecidedAt(OffsetDateTime.now());
        if (request != null && StringUtils.hasText(request.getDecisionNotes())) {
            ext.setDecisionNotes(request.getDecisionNotes().trim());
        }
        return toResponse(extensionRepository.save(ext), null, null, null);
    }

    private List<DurationExtensionResponse> enrichList(List<ScheduleDurationExtension> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> projectIds = items.stream().map(ScheduleDurationExtension::getProjectId).collect(Collectors.toSet());
        Set<UUID> activityUuids = items.stream().map(ScheduleDurationExtension::getActivityUuid).collect(Collectors.toSet());
        Set<Long> accountIds = items.stream().map(ScheduleDurationExtension::getRequestedBy).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<Long, Project> projects = new HashMap<>();
        for (Project p : projectRepository.findAllById(projectIds)) {
            projects.put(p.getId(), p);
        }
        Map<UUID, ScheduleActivity> activities = new HashMap<>();
        for (UUID id : activityUuids) {
            activityRepository.findById(id).ifPresent(a -> activities.put(id, a));
        }
        Map<Long, Account> accounts = new HashMap<>();
        for (Account a : accountRepository.findAllById(accountIds)) {
            accounts.put(a.getId(), a);
        }

        return items.stream()
                .map(ext -> toResponse(
                        ext,
                        activities.get(ext.getActivityUuid()),
                        projects.get(ext.getProjectId()),
                        accounts.get(ext.getRequestedBy())))
                .toList();
    }

    private DurationExtensionResponse toResponse(
            ScheduleDurationExtension ext,
            ScheduleActivity activity,
            Project project,
            Account requester) {
        if (project == null && ext.getProjectId() != null) {
            project = projectRepository.findById(ext.getProjectId()).orElse(null);
        }
        if (activity == null && ext.getActivityUuid() != null) {
            activity = activityRepository.findById(ext.getActivityUuid()).orElse(null);
        }
        if (requester == null && ext.getRequestedBy() != null) {
            requester = accountRepository.findById(ext.getRequestedBy()).orElse(null);
        }
        DelayReasonCode code = ext.getDelayReasonCode();
        return DurationExtensionResponse.builder()
                .uuid(ext.getUuid())
                .companyId(ext.getCompanyId())
                .projectId(ext.getProjectId())
                .activityUuid(ext.getActivityUuid())
                .requestedBy(ext.getRequestedBy())
                .currentDurationWorkingDays(ext.getCurrentDurationWorkingDays())
                .requestedDurationWorkingDays(ext.getRequestedDurationWorkingDays())
                .delayReasonCode(code)
                .delayReasonText(ext.getDelayReasonText())
                .status(ext.getStatus())
                .decidedBy(ext.getDecidedBy())
                .decidedAt(ext.getDecidedAt())
                .decisionNotes(ext.getDecisionNotes())
                .createdAt(ext.getCreatedAt())
                .projectName(project != null ? project.getName() : null)
                .activityName(activity != null ? activity.getName() : null)
                .requestedByName(requester != null ? displayName(requester) : null)
                .delayReasonLabel(code != null ? code.getLabel() : null)
                .build();
    }

    private static String formatDelayReason(ScheduleDurationExtension ext) {
        DelayReasonCode code = ext.getDelayReasonCode();
        if (code == DelayReasonCode.OTHER) {
            return StringUtils.hasText(ext.getDelayReasonText())
                    ? ext.getDelayReasonText().trim()
                    : code.getLabel();
        }
        if (StringUtils.hasText(ext.getDelayReasonText())) {
            return code.getLabel() + " — " + ext.getDelayReasonText().trim();
        }
        return code.getLabel();
    }

    private static String displayName(Account account) {
        if (account.getFullName() != null && !account.getFullName().isBlank()) {
            return account.getFullName().trim();
        }
        return account.getEmail();
    }

    private ScheduleDurationExtension requirePending(UUID uuid) {
        ScheduleDurationExtension ext = extensionRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Duration extension not found"));
        if (ext.getStatus() != ScheduleDurationExtensionStatus.PENDING) {
            throw new BadRequestException("Duration extension is already " + ext.getStatus());
        }
        return ext;
    }

    private boolean isTeamSiteEngineer(AuthPrincipal principal, Long projectId) {
        if (principal == null || projectId == null) {
            return false;
        }
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            return false;
        }
        return teamAssignmentRepository.existsByProjectIdAndAccountIdAndCompanyIdAndRole(
                projectId, principal.getAccountId(), companyId, ProjectTeamRole.SITE_ENGINEER);
    }

    private UUID requireCompany() {
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            throw new ForbiddenException("Company context required");
        }
        return companyId;
    }

    private AuthPrincipal requireAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }

    private AuthPrincipal requirePmOrAdmin() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() == null) {
            throw new ForbiddenException("PM/Admin access required");
        }
        boolean allowed = principal.getRoles().contains(Role.ADMIN)
                || principal.getRoles().contains(Role.SUPER_ADMIN)
                || principal.getRoles().contains(Role.BUSINESS_OWNER)
                || principal.getRoles().contains(Role.PROJECT_MANAGER);
        if (!allowed) {
            throw new ForbiddenException("PM/Admin access required");
        }
        return principal;
    }
}
