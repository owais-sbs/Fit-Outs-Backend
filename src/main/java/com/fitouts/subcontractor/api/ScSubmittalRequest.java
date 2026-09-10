package com.fitouts.subcontractor.api;

import java.util.UUID;

import lombok.Data;

@Data
public class ScSubmittalRequest {
    private UUID packageUuid;
    private Long projectId;
    private String title;
    private String submittalType;
    private String reviewCode;
    private String filePaths;
}
