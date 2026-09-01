package com.fitouts.subcontractor.api;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubcontractorProjectSummary {
    private Long projectId;
    private String projectName;
    private String location;
    private String status;
    private String projectType;
    private String assignedManager;
    private int packageCount;
    private int activePackageCount;
}
