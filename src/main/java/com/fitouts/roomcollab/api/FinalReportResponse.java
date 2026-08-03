package com.fitouts.roomcollab.api;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitouts.roomcollab.domain.RoomTaskType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FinalReportResponse {
    private Long projectId;
    private String projectName;
    @Builder.Default
    private List<FinalReportRoom> rooms = new ArrayList<>();

    @Getter
    @Builder
    public static class FinalReportRoom {
        private UUID roomUuid;
        private String floorLabel;
        private String roomName;
        @Builder.Default
        private List<FinalReportItem> items = new ArrayList<>();
    }

    @Getter
    @Builder
    public static class FinalReportItem {
        private UUID taskUuid;
        private String title;
        private RoomTaskType taskType;
        private OffsetDateTime approvedAt;
        private Integer clientApprovalDays;
        private Integer revisionCount;
        private String fileName;
        private UUID versionUuid;
        private String downloadUrl;
    }
}
