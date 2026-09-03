package com.fitouts.schedule.api;

import java.util.UUID;

import lombok.Data;

@Data
public class ScheduleFromRoomTaskRequest {
    private UUID roomTaskId;
}
