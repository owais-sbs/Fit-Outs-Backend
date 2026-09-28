package com.fitouts.schedule.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.schedule.domain.SchedulePublishStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleActivityResponse {
    private UUID uuid;
    private Long projectId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private int percentComplete;
    private BigDecimal weight;
    private UUID parentUuid;
    private UUID projectRoomId;
    private UUID roomTaskId;
    private Long assigneeAccountId;
    private SchedulePublishStatus publishStatus;
    private int sortOrder;
    private String delayReason;
    private OffsetDateTime updatedAt;
    private String roomName;
    private String roomTaskTitle;
    private String roomTaskStatus;
    private String assigneeName;

    // CPM fields (Module 43) — needed by the Gantt after template apply
    private String activityCode;
    private String wbsPhase;
    private Integer durationWorkingDays;
    private boolean milestone;
    private boolean lockedDuration;
    private String constraintNote;
    private boolean critical;
    private Integer totalFloat;
    private Integer freeFloat;

    /** Present when this bar was created from a BOQ line (Mode 2 / unmatched Mode 3). */
    private UUID boqLineId;
    /** Blend attachments on this activity. */
    private int attachedBoqLineCount;
    private java.util.List<AttachedBoqLine> attachedBoqLines;

    @Data
    @Builder
    public static class AttachedBoqLine {
        private UUID boqLineId;
        private String description;
        private String categoryCode;
        private String categoryName;
        private String matchSource;
        private Integer sortOrder;
    }
}
