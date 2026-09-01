package com.fitouts.validation.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.billing.application.BillingService;
import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.schedule.domain.ActivityProgressUpdate;
import com.fitouts.schedule.domain.ActivityProgressUpdateRepository;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;
import com.fitouts.shared.error.NotFoundException;
import com.fitouts.validation.api.ProgressValidationResponse;
import com.fitouts.validation.api.ValidationRejectRequest;
import com.fitouts.validation.domain.ProgressValidation;
import com.fitouts.validation.domain.ProgressValidationRepository;
import com.fitouts.validation.domain.ProgressValidationStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProgressValidationService {

    private final ProgressValidationRepository validationRepository;
    private final ActivityProgressUpdateRepository progressRepository;
    private final ScheduleActivityRepository activityRepository;
    private final ProjectService projectService;
    private final BillingService billingService;

    @Transactional
    public ProgressValidation createPendingForProgress(ActivityProgressUpdate update) {
        if (validationRepository.existsByProgressUpdateUuid(update.getUuid())) {
            return validationRepository.findByProgressUpdateUuid(update.getUuid()).orElseThrow();
        }
        ProgressValidation validation = new ProgressValidation();
        validation.setProgressUpdateUuid(update.getUuid());
        validation.setActivityUuid(update.getActivityUuid());
        validation.setProjectId(update.getProjectId());
        validation.setCompanyId(update.getCompanyId());
        validation.setStatus(ProgressValidationStatus.PENDING);
        return validationRepository.save(validation);
    }

    @Transactional
    public ProgressValidationResponse submitForValidation(UUID activityUuid, UUID progressUuid) {
        requireStaff();
        UUID companyId = requireCompany();

        ScheduleActivity activity = activityRepository.findByUuidAndCompanyId(activityUuid, companyId)
                .orElseThrow(() -> new NotFoundException("Activity not found"));

        ActivityProgressUpdate progress = progressRepository.findById(progressUuid)
                .orElseThrow(() -> new NotFoundException("Progress update not found"));
        if (!progress.getActivityUuid().equals(activity.getUuid())) {
            throw new BadRequestException("Progress update does not belong to this activity");
        }
        if (!companyId.equals(progress.getCompanyId())) {
            throw new ForbiddenException("Progress update not in your company");
        }

        ProgressValidation validation = createPendingForProgress(progress);
        return toResponse(validation);
    }

    @Transactional(readOnly = true)
    public List<ProgressValidationResponse> inbox() {
        requirePmOrAdmin();
        UUID companyId = requireCompany();
        return validationRepository
                .findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, ProgressValidationStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProgressValidationResponse> listByProject(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();
        return validationRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProgressValidationResponse approve(UUID uuid) {
        AuthPrincipal principal = requirePmOrAdmin();
        ProgressValidation validation = requirePending(uuid);
        ActivityProgressUpdate progress = progressRepository.findById(validation.getProgressUpdateUuid())
                .orElseThrow(() -> new NotFoundException("Progress update not found"));
        ScheduleActivity activity = activityRepository
                .findByUuidAndCompanyId(validation.getActivityUuid(), requireCompany())
                .orElseThrow(() -> new NotFoundException("Activity not found"));

        activity.setPercentComplete(progress.getPercentComplete());
        activityRepository.save(activity);

        validation.setStatus(ProgressValidationStatus.APPROVED);
        validation.setDecidedBy(principal.getAccountId());
        validation.setDecidedAt(OffsetDateTime.now());
        validation.setReason(null);
        validationRepository.save(validation);

        billingService.evaluateTriggersForActivity(activity.getUuid());
        return toResponse(validation);
    }

    @Transactional
    public ProgressValidationResponse reject(UUID uuid, ValidationRejectRequest request) {
        AuthPrincipal principal = requirePmOrAdmin();
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason is required");
        }
        ProgressValidation validation = requirePending(uuid);
        validation.setStatus(ProgressValidationStatus.REJECTED);
        validation.setDecidedBy(principal.getAccountId());
        validation.setDecidedAt(OffsetDateTime.now());
        validation.setReason(request.getReason().trim());
        return toResponse(validationRepository.save(validation));
    }

    private ProgressValidation requirePending(UUID uuid) {
        ProgressValidation validation = validationRepository.findByUuidAndCompanyId(uuid, requireCompany())
                .orElseThrow(() -> new NotFoundException("Validation not found"));
        if (validation.getStatus() != ProgressValidationStatus.PENDING) {
            throw new BadRequestException("Validation is already " + validation.getStatus());
        }
        return validation;
    }

    private ProgressValidationResponse toResponse(ProgressValidation v) {
        return ProgressValidationResponse.builder()
                .uuid(v.getUuid())
                .progressUpdateUuid(v.getProgressUpdateUuid())
                .activityUuid(v.getActivityUuid())
                .projectId(v.getProjectId())
                .status(v.getStatus())
                .decidedBy(v.getDecidedBy())
                .decidedAt(v.getDecidedAt())
                .reason(v.getReason())
                .createdAt(v.getCreatedAt())
                .build();
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
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

    private AuthPrincipal requireStaff() {
        AuthPrincipal principal = requireAuthenticated();
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
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
