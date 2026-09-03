package com.fitouts.schedule.api;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleCalendarEventResponse {
    private UUID uuid;
    private Long projectId;
    private String projectName;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private int percentComplete;
    private Long assigneeAccountId;
    private String assigneeName;
    private UUID projectRoomId;
    private String roomName;
    private UUID roomTaskId;
    private String roomTaskTitle;
}
