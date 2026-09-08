package com.fitouts.schedule.api;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** Template with its activities, logic and long-lead items, for the template admin screen. */
@Getter
@Builder
public class ScheduleTemplateDetailResponse {

    private ScheduleTemplateResponse header;
    private List<TemplateActivityView> activities;
    private List<SchedulePreviewResponse.PreviewDependency> dependencies;
    private List<ProcurementItemView> procurementItems;

    /**
     * Activities whose predecessor logic is demonstrably incomplete: the source file places
     * them far later than their stated predecessors require. Surfaced rather than patched.
     */
    private List<String> incompleteLogicWarnings;

    @Getter
    @Builder
    public static class TemplateActivityView {
        private String activityCode;
        private String name;
        private String wbsPhase;
        private String tradePackageCode;
        private String tradeLabel;
        private int baseDurationDays;
        private String scalingMethod;
        private String scalingDriver;
        private boolean milestone;
        private boolean lockedDuration;
        private String scopeToggleCode;
        private String constraintNote;
        private Integer seedStartWd;
        private Integer seedFinishWd;
        private Integer computedStartWd;
        private Integer computedFinishWd;
        private String varianceNote;
        private int sortOrder;
    }

    @Getter
    @Builder
    public static class ProcurementItemView {
        private String itemName;
        private Integer leadTimeCalendarDaysMin;
        private Integer leadTimeCalendarDaysMax;
        private String leadTimeRaw;
        private String orderByRule;
        private String siteInfoNeeded;
        private String riskNote;
        private String linkedInstallActivityCode;
    }
}
