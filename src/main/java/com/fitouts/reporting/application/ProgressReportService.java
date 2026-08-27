package com.fitouts.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fitouts.auth.domain.Role;
import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.project.application.ProjectService;
import com.fitouts.project.domain.Project;
import com.fitouts.reporting.api.ProgressReportResponse;
import com.fitouts.schedule.domain.ScheduleActivity;
import com.fitouts.schedule.domain.ScheduleActivityRepository;
import com.fitouts.schedule.domain.ScheduleBaseline;
import com.fitouts.schedule.domain.ScheduleBaselineActivity;
import com.fitouts.schedule.domain.ScheduleBaselineActivityRepository;
import com.fitouts.schedule.domain.ScheduleBaselineRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ForbiddenException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProgressReportService {

    private final ScheduleActivityRepository activityRepository;
    private final ScheduleBaselineRepository baselineRepository;
    private final ScheduleBaselineActivityRepository baselineActivityRepository;
    private final ProjectService projectService;

    @Transactional(readOnly = true)
    public ProgressReportResponse getReport(Long projectId) {
        requireStaff();
        Project project = requireProject(projectId);
        UUID companyId = CompanyContext.get();

        List<ScheduleActivity> activities = activityRepository
                .findByProjectIdAndCompanyIdOrderBySortOrderAscStartDateAsc(project.getId(), companyId);

        ScheduleBaseline latestBaseline = baselineRepository
                .findByProjectIdAndCompanyIdOrderByCreatedAtDesc(project.getId(), companyId)
                .stream()
                .findFirst()
                .orElse(null);

        Map<UUID, ScheduleBaselineActivity> baselineByActivity = Map.of();
        if (latestBaseline != null) {
            baselineByActivity = baselineActivityRepository.findByBaselineUuid(latestBaseline.getUuid())
                    .stream()
                    .collect(Collectors.toMap(ScheduleBaselineActivity::getActivityUuid, a -> a, (a, b) -> a));
        }

        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal weightedPercentSum = BigDecimal.ZERO;
        Set<String> delayReasons = new LinkedHashSet<>();
        List<ProgressReportResponse.ActivityProgressRow> rows = new ArrayList<>();

        for (ScheduleActivity activity : activities) {
            BigDecimal weight = activity.getWeight() != null ? activity.getWeight() : BigDecimal.ONE;
            weightSum = weightSum.add(weight);
            weightedPercentSum = weightedPercentSum.add(
                    weight.multiply(BigDecimal.valueOf(activity.getPercentComplete())));

            ScheduleBaselineActivity baseline = baselineByActivity.get(activity.getUuid());
            if (StringUtils.hasText(activity.getDelayReason())) {
                delayReasons.add(activity.getDelayReason().trim());
            }

            rows.add(ProgressReportResponse.ActivityProgressRow.builder()
                    .activityUuid(activity.getUuid())
                    .name(activity.getName())
                    .percent(activity.getPercentComplete())
                    .weight(weight)
                    .start(activity.getStartDate())
                    .end(activity.getEndDate())
                    .baselineStart(baseline != null ? baseline.getStartDate() : null)
                    .baselineEnd(baseline != null ? baseline.getEndDate() : null)
                    .delayReason(activity.getDelayReason())
                    .build());
        }

        BigDecimal weightedCompletion = BigDecimal.ZERO;
        if (weightSum.compareTo(BigDecimal.ZERO) > 0) {
            weightedCompletion = weightedPercentSum
                    .divide(weightSum, 2, RoundingMode.HALF_UP);
        }

        String summary = buildSummary(project.getName(), weightedCompletion, activities.size(), delayReasons.size());

        return ProgressReportResponse.builder()
                .projectId(project.getId())
                .weightedCompletionPercent(weightedCompletion)
                .activities(rows)
                .delayReasonCodes(new ArrayList<>(delayReasons))
                .summary(summary)
                .baselineName(latestBaseline != null ? latestBaseline.getName() : null)
                .baselineUuid(latestBaseline != null ? latestBaseline.getUuid() : null)
                .build();
    }

    private String buildSummary(String projectName, BigDecimal weightedCompletion, int activityCount, int delayCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(projectName != null ? projectName : "Project")
                .append(" is ")
                .append(weightedCompletion.stripTrailingZeros().toPlainString())
                .append("% complete across ")
                .append(activityCount)
                .append(" activities");
        if (delayCount > 0) {
            sb.append(" with ").append(delayCount).append(" delay reason code(s) reported");
        }
        sb.append('.');
        return sb.toString();
    }

    private Project requireProject(Long projectId) {
        Project project = projectService.getById(projectId);
        UUID companyId = CompanyContext.get();
        if (companyId == null || project.getCompanyId() == null || !companyId.equals(project.getCompanyId())) {
            throw new ForbiddenException("Project not in your company");
        }
        return project;
    }

    private AuthPrincipal requireStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        if (principal.getRoles() != null && principal.getRoles().stream().allMatch(r -> r == Role.CLIENT)) {
            throw new ForbiddenException("Staff access required");
        }
        return principal;
    }
}
