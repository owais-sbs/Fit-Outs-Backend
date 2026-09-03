package com.fitouts.project.api;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ProjectTeamAssignmentSyncRequest {

    private List<ProjectTeamAssignmentItemRequest> assignments = new ArrayList<>();
}
