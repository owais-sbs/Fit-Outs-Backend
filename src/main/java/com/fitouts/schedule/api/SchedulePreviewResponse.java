package com.fitouts.schedule.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/** What applying this template would produce, before anything is written. */
@Getter
@Builder
public class SchedulePreviewResponse {

    private UUID templateUuid;
    private String templateCode;
    private String templateName;

    private LocalDate startDate;
    private LocalDate finishDate;
    private int workingDays;
    private int calendarDays;

    /** The template's published headline, kept alongside so the variance is visible. */
    private Integer templateTargetWorkingDays;
    private String targetVarianceNote;

    private int activityCount;
    private int excludedByToggleCount;

    private List<PreviewActivity> activities;
    private List<PreviewDependency> dependencies;
    private List<List<String>> criticalPaths;
    private List<OrderByPreview> orderByDates;
    private List<ApprovalTargetPreview> approvalTargets;
    private List<PackagePreview> packages;

    /** Logic faults, incomplete predecessors, unscaled activities. */
    private List<String> warnings;

    /** Things that stop a publish outright, such as an order-by date already in the past. */
    private List<String> blockers;

    private boolean requiresFastTrackAcknowledgement;
    private List<String> fastTrackConditions;
    private boolean canPublish;

    @Getter
    @Builder
    public static class PreviewActivity {
        private String activityCode;
        private String name;
        private String wbsPhase;
        private String tradePackageCode;
        private int durationWorkingDays;
        private int baseDurationWorkingDays;
        private String scalingMethod;
        private String scalingExplanation;
        private LocalDate earlyStart;
        private LocalDate earlyFinish;
        private LocalDate lateStart;
        private LocalDate lateFinish;
        private int totalFloat;
        private int freeFloat;
        private boolean critical;
        private boolean milestone;
        private boolean lockedDuration;
        private String constraintNote;
        /** Set when the source file's own day numbers disagree with the computed dates. */
        private String seedVarianceNote;
    }

    @Getter
    @Builder
    public static class PreviewDependency {
        private String predecessorCode;
        private String successorCode;
        private String type;
        private int lagWorkingDays;
        private boolean locked;
        private String lockReason;
    }

    @Getter
    @Builder
    public static class OrderByPreview {
        private String itemName;
        private int leadTimeCalendarDays;
        private String installActivityCode;
        private LocalDate installStartDate;
        private LocalDate orderByDate;
        private boolean overdue;
        private String riskNote;
        private String siteInfoNeeded;
    }

    @Getter
    @Builder
    public static class ApprovalTargetPreview {
        private String permitTypeCode;
        private String permitTypeName;
        private String authorityName;
        private Integer slaWorkingDays;
        private LocalDate targetSubmissionDate;
        private LocalDate requiredApprovalDate;
        private String blocksActivityCode;
        private boolean atRisk;
    }

    @Getter
    @Builder
    public static class PackagePreview {
        private String tradePackageCode;
        private String name;
        private LocalDate plannedStart;
        private LocalDate plannedFinish;
        private int activityCount;
    }
}
