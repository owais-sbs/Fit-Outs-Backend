package com.fitouts.snag.api;

import java.time.LocalDate;
import java.util.UUID;

import com.fitouts.snag.domain.SnagSeverity;
import com.fitouts.snag.domain.SnagStatus;

import lombok.Data;

@Data
public class SnagRequest {
    private String title;
    private String description;
    private String location;
    private UUID projectRoomId;
    private UUID activityUuid;
    private String photoPaths;
    private SnagStatus status;
    private SnagSeverity severity;
    private LocalDate dueDate;
    private Long assigneeAccountId;
    private Boolean clientVisible;
}
