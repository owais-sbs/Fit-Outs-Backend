package com.fitouts.subcontractor.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fitouts.snag.domain.SnagSeverity;
import com.fitouts.snag.domain.SnagStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScSnagPortalResponse {
    private UUID uuid;
    private Long projectId;
    private String projectName;
    private String title;
    private String description;
    private String location;
    private SnagStatus status;
    private SnagSeverity severity;
    private LocalDate dueDate;
    private OffsetDateTime createdAt;
}
