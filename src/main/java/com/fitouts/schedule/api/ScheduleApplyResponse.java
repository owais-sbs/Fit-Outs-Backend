package com.fitouts.schedule.api;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** What the apply actually wrote, so the PM sees the full blast radius of one click. */
@Getter
@Builder
public class ScheduleApplyResponse {

    private SchedulePreviewResponse preview;

    private int activitiesWritten;
    private int dependenciesWritten;
    private int activitiesReplaced;

    private List<SchedulePreviewResponse.PackagePreview> packagesUpserted;
    private List<String> billingMilestonesCreated;
    private List<String> holdPointsCreated;
    private List<SchedulePreviewResponse.ApprovalTargetPreview> approvalTargetsUpdated;
    private int orderByRowsWritten;

    private String note;
}
